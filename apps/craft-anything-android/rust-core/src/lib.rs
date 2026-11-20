//! Minimal Android JNI bridge. Keep this API small: Android owns presentation and platform
//! permissions; the Rust engine remains the source of truth for tools and evidence.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use craft_anything_engine::{CallOptions, Caller, Engine, Session, Settings};
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::sync::{Arc, OnceLock};

static ENGINE: OnceLock<Arc<Engine>> = OnceLock::new();

fn engine() -> &'static Arc<Engine> {
    ENGINE.get_or_init(|| {
        let session = Session::new(Settings::default());
        Engine::new(session).into()
    })
}

fn into_java_string(env: &mut JNIEnv<'_>, value: String) -> jstring {
    match env.new_string(value) {
        Ok(value) => value.into_raw(),
        Err(error) => {
            log::error!("couldn't allocate a Java string for the Android bridge: {error}");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeVersion(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    into_java_string(&mut env, env!("CARGO_PKG_VERSION").to_string())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeToolCatalog(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    let registry = engine().registry();
    let tools: Vec<_> = registry.all().iter().map(|tool| {
        serde_json::json!({
            "id": tool.id,
            "title": tool.title,
            "summary": tool.summary,
            "toolset": tool.toolset.id(),
            "effects": tool.effects.bits(),
        })
    }).collect();
    let json = serde_json::to_string(&tools).unwrap_or_else(|error| {
        log::error!("couldn't serialize the Android tool catalog: {error}");
        "[]".to_string()
    });
    into_java_string(&mut env, json)
}

/// Dispatch one registered engine tool. The caller must run this off the Android UI thread.
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeCallTool(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    tool_id: JString<'_>,
    params_json: JString<'_>,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let tool_id = env.get_string(&tool_id).map_err(|error| error.to_string())?.to_string_lossy().into_owned();
        let params_text = env.get_string(&params_json).map_err(|error| error.to_string())?.to_string_lossy().into_owned();
        let params: serde_json::Value = serde_json::from_str(&params_text).map_err(|error| format!("Invalid tool parameters: {error}"))?;
        let output = engine().call(&tool_id, params, CallOptions::new(Caller::Ui)).map_err(|error| error.message)?;
        serde_json::to_string(&serde_json::json!({"ok": true, "output": output.to_json()}))
            .map_err(|error| format!("Couldn't encode tool result: {error}"))
    })();
    let json = result.unwrap_or_else(|message| serde_json::json!({"ok": false, "error": message}).to_string());
    into_java_string(&mut env, json)
}


#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::Value;

    fn test_engine() -> Engine {
        Engine::new(Session::new(Settings::default()))
    }

    #[test]
    fn catalogue_includes_the_file_open_tool() {
        let catalog = tool_catalog_json(&test_engine());
        let value = serde_json::from_str::<Value>(&catalog).unwrap_or(Value::Null);
        let includes_open = value.as_array().is_some_and(|items| {
            items.iter().any(|item| item["id"] == "core_target_open")
        });
        assert!(includes_open, "the Android catalogue must expose core_target_open");
    }

    #[test]
    fn dispatch_returns_the_real_session_status() {
        let response = dispatch_tool(&test_engine(), "core_session_show", "{}");
        let value = serde_json::from_str::<Value>(&response).unwrap_or(Value::Null);
        assert_eq!(value["ok"], Value::Bool(true));
        assert_eq!(value["output"]["result"]["open"], Value::Bool(false));
    }

    #[test]
    fn dispatch_reports_malformed_parameters_as_a_structured_error() {
        let response = dispatch_tool(&test_engine(), "core_session_show", "{");
        let value = serde_json::from_str::<Value>(&response).unwrap_or(Value::Null);
        assert_eq!(value["ok"], Value::Bool(false));
        assert!(
            value["error"].as_str().is_some_and(|message| message.contains("Invalid tool parameters")),
            "malformed JSON must produce a useful bridge error"
        );
    }
}
