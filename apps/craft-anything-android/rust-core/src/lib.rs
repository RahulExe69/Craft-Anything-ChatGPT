//! Minimal Android JNI bridge. Keep this API small: Android owns presentation and platform
//! permissions; the Rust engine remains the source of truth for tools and evidence.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use craft_anything_engine::{Engine, Session, Settings};
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
