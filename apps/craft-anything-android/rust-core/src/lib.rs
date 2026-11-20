//! Minimal Android JNI bridge. Keep this API small: Android owns presentation and platform
//! permissions; the Rust engine remains the source of truth for tools and evidence.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use craft_anything_engine::{CallOptions, Caller, Consent, Engine, Session, Settings};
use serde_json::Value;
use std::sync::{Arc, OnceLock};

static ENGINE: OnceLock<Arc<Engine>> = OnceLock::new();

fn settings_for_app_data_dir(app_data_dir: &str) -> Result<Settings, String> {
    let root = std::path::PathBuf::from(app_data_dir);
    if !root.is_absolute() {
        return Err("Android app data directory must be an absolute path.".to_string());
    }
    let projects_dir = root.join("projects");
    std::fs::create_dir_all(&projects_dir)
        .map_err(|error| format!("Couldn't create the private projects directory: {error}"))?;
    let mut settings = Settings::default();
    settings.projects_dir = Some(projects_dir);
    Ok(settings)
}

fn initialize_engine(app_data_dir: &str) -> Result<(), String> {
    if ENGINE.get().is_some() {
        return Ok(());
    }
    let settings = settings_for_app_data_dir(app_data_dir)?;
    let configured = Arc::new(Engine::new(Session::new(settings)));
    let _ = ENGINE.set(configured);
    if ENGINE.get().is_some() {
        Ok(())
    } else {
        Err("The Rust engine couldn't be initialized.".to_string())
    }
}

fn engine() -> &'static Arc<Engine> {
    ENGINE.get_or_init(|| Engine::new(Session::new(Settings::default())).into())
}

/// Serialize the tools registered by the engine for the native Android browser.
fn tool_catalog_json(engine: &Engine) -> String {
    let tools: Vec<_> = engine.registry().all().iter().map(|tool| {
        serde_json::json!({
            "id": tool.id,
            "title": tool.title,
            "summary": tool.summary,
            "toolset": tool.toolset.id(),
            "toolset_title": tool.toolset.title(),
            "effects": tool.effects.bits(),
            "requires_consent": tool.effects.needs_consent(),
            "input_schema": (tool.input_schema)(),
        })
    }).collect();

    serde_json::to_string(&tools).unwrap_or_else(|error| {
        log::error!("couldn't serialize the Android tool catalog: {error}");
        "[]".to_string()
    })
}

/// Dispatch a registered engine tool and return a stable JSON envelope.
fn dispatch_tool(engine: &Engine, tool_id: &str, params_json: &str, consent_granted: bool) -> String {
    let result = (|| -> Result<Value, String> {
        let params: Value = serde_json::from_str(params_json)
            .map_err(|error| format!("Invalid tool parameters: {error}"))?;
        let consent = if consent_granted { Consent::Granted } else { Consent::NotAsked };
        let options = CallOptions::new(Caller::Ui).with_consent(consent);
        let output = engine.call(tool_id, params, options)
            .map_err(|error| error.message)?;
        Ok(serde_json::json!({ "ok": true, "output": output.to_json() }))
    })();

    match result {
        Ok(value) => value.to_string(),
        Err(message) => serde_json::json!({ "ok": false, "error": message }).to_string(),
    }
}

// The Rust workspace continues to forbid unsafe code everywhere else. This one module is the
// ABI boundary: only the JNI export-name attributes are allowed here; JNI access itself uses
// jni-rs' safe EnvUnowned::with_env API, and no raw-pointer operations are performed.
#[allow(unsafe_code)]
mod jni_exports {
    use super::{dispatch_tool, engine, tool_catalog_json};
    use jni::errors::ThrowRuntimeExAndDefault;
    use jni::objects::{JObject, JString};
    use jni::sys::{jboolean, jstring};
    use jni::EnvUnowned;

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeInitialize<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _this: JObject<'frame>,
        app_data_dir: JString<'frame>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let path = app_data_dir.try_to_string(env)?;
                let result = match super::initialize_engine(&path) {
                    Ok(()) => "ok".to_string(),
                    Err(error) => format!("error:{error}"),
                };
                Ok(env.new_string(result)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeVersion<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _this: JObject<'frame>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                Ok(env.new_string(env!("CARGO_PKG_VERSION"))?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[unsafe(no_mangle)]
    pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeToolCatalog<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _this: JObject<'frame>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let catalog = tool_catalog_json(engine());
                Ok(env.new_string(catalog)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    /// Dispatch one registered engine tool. Kotlin must call this away from the UI thread.
    #[unsafe(no_mangle)]
    pub extern "system" fn Java_ai_storytold_craftanything_MainActivity_nativeCallTool<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _class: JClass<'frame>,
        tool_id: JString<'frame>,
        params_json: JString<'frame>,
        consent_granted: jboolean,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let tool_id = tool_id.try_to_string(env)?;
                let params_text = params_json.try_to_string(env)?;
                let response = dispatch_tool(engine(), &tool_id, &params_text, consent_granted != 0);
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_engine() -> Engine {
        Engine::new(Session::new(Settings::default()))
    }


    #[test]
    fn android_settings_use_a_private_projects_directory() {
        let root = std::env::temp_dir().join("craft-anything-android-settings-test");
        let path = root.to_string_lossy().into_owned();
        let settings = settings_for_app_data_dir(&path);
        assert!(settings.is_ok(), "a valid app directory should configure successfully");
        if let Ok(settings) = settings {
            assert_eq!(settings.projects_dir, Some(root.join("projects")));
        }
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn android_settings_reject_relative_data_directories() {
        assert!(settings_for_app_data_dir("relative/app-data").is_err());
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
    fn catalogue_exposes_input_schemas_and_consent_requirements() {
        let catalog = tool_catalog_json(&test_engine());
        let value = serde_json::from_str::<Value>(&catalog).unwrap_or(Value::Null);
        assert!(value.is_array(), "the tool catalogue must be a JSON array");
        let empty_items: Vec<Value> = Vec::new();
        let items = value.as_array().unwrap_or(&empty_items);
        assert!(items.iter().all(|item| item["input_schema"].is_object()));
        assert!(items.iter().all(|item| item["requires_consent"].is_boolean()));
        assert!(
            items.iter().any(|item| item["requires_consent"] == Value::Bool(true)),
            "the registry should expose at least one consent-gated tool"
        );
    }


    #[test]
    fn consent_is_scoped_to_the_current_invocation() {
        let unapproved = CallOptions::new(Caller::Ui);
        let approved = CallOptions::new(Caller::Ui).with_consent(Consent::Granted);
        assert_eq!(unapproved.consent, Consent::NotAsked);
        assert_eq!(approved.consent, Consent::Granted);
    }

    #[test]
    fn dispatch_returns_the_real_session_status() {
        let response = dispatch_tool(&test_engine(), "core_session_show", "{}", false);
        let value = serde_json::from_str::<Value>(&response).unwrap_or(Value::Null);
        assert_eq!(value["ok"], Value::Bool(true));
        assert_eq!(value["output"]["result"]["open"], Value::Bool(false));
    }

    #[test]
    fn dispatch_reports_malformed_parameters_as_a_structured_error() {
        let response = dispatch_tool(&test_engine(), "core_session_show", "{", false);
        let value = serde_json::from_str::<Value>(&response).unwrap_or(Value::Null);
        assert_eq!(value["ok"], Value::Bool(false));
        assert!(
            value["error"].as_str().is_some_and(|message| message.contains("Invalid tool parameters")),
            "malformed JSON must produce a useful bridge error"
        );
    }
}
