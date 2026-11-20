//! Minimal Android JNI bridge. Keep this API small: Android owns presentation and platform
//! permissions; the Rust engine remains the source of truth for tools and evidence.
#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

use craft_anything_ai::{AiHub, BackendConfig, BackendKind, Conversation, KeyStore, Role};
use craft_anything_engine::{CallOptions, Caller, Consent, Engine, Session, Settings};
use craft_anything_platform::sync::Mutex;
use serde::Deserialize;
use serde_json::Value;
use std::sync::{Arc, OnceLock};

static ENGINE: OnceLock<Arc<Engine>> = OnceLock::new();
static AI_HUB: OnceLock<AiHub> = OnceLock::new();
static AI_CHAT: OnceLock<Mutex<Option<Box<dyn Conversation>>>> = OnceLock::new();

fn settings_for_app_data_dir(app_data_dir: &str) -> Result<Settings, String> {
    let root = std::path::PathBuf::from(app_data_dir);
    if !root.is_absolute() {
        return Err("Android app data directory must be an absolute path.".to_string());
    }
    let projects_dir = root.join("projects");
    let engines_dir = root.join("engines");
    std::fs::create_dir_all(&projects_dir).map_err(|error| format!("Couldn't create the private projects directory: {error}"))?;
    std::fs::create_dir_all(&engines_dir).map_err(|error| format!("Couldn't create the private engines directory: {error}"))?;
    let settings_path = root.join("config.toml");
    let mut settings = match std::fs::read_to_string(&settings_path) {
        Ok(contents) => craft_anything_engine::settings::read_settings(&contents).map_err(|error| format!("Couldn't load Android app settings: {error}"))?,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Settings::default(),
        Err(error) => return Err(format!("Couldn't read Android app settings: {error}")),
    };
    settings.projects_dir = Some(projects_dir);
    settings.engines.store_dir = Some(engines_dir);
    Ok(settings)
}

fn initialize_engine(app_data_dir: &str) -> Result<(), String> {
    if ENGINE.get().is_none() {
        let settings = settings_for_app_data_dir(app_data_dir)?;
        let root = std::path::PathBuf::from(app_data_dir);
        let mut session = Session::isolated(&root);
        session.settings = settings;
        let configured = Engine::shared(session, craft_anything_ai::tools());
        let _ = ENGINE.set(configured);
    }
    let configured = engine()?.clone();
    if AI_HUB.get().is_none() {
        let root = std::path::PathBuf::from(app_data_dir);
        let hub = AiHub::with_stores(configured, root.join("config.toml"), KeyStore::at_config_dir(&root));
        let _ = AI_HUB.set(hub);
    }
    Ok(())
}

fn engine() -> Result<&'static Arc<Engine>, String> {
    ENGINE.get().ok_or_else(|| "The Rust engine has not been initialized.".to_string())
}

fn ai_hub() -> Result<&'static AiHub, String> {
    AI_HUB.get().ok_or_else(|| "The AI runtime has not been initialized.".to_string())
}

fn ai_chat() -> &'static Mutex<Option<Box<dyn Conversation>>> {
    AI_CHAT.get_or_init(|| Mutex::new(None))
}

fn ai_ok(output: Value) -> String {
    serde_json::json!({ "ok": true, "output": output }).to_string()
}

fn ai_error(message: impl Into<String>) -> String {
    serde_json::json!({ "ok": false, "error": message.into() }).to_string()
}

/// Return provider metadata only. API keys never cross the JNI boundary.
fn ai_status_json(hub: &AiHub) -> String {
    let config = hub.config();
    let backends: Vec<_> = config
        .backends
        .iter()
        .map(|entry| {
            serde_json::json!({
                "name": entry.config.name,
                "kind": entry.config.kind,
                "model": entry.config.model,
                "base_url": entry.config.base_url,
                "enabled": entry.config.enabled,
                "has_key": hub.has_key(&entry.config.name),
            })
        })
        .collect();
    ai_ok(serde_json::json!({
        "backends": backends,
        "roles": {
            "lead": hub.chosen(Role::Lead),
            "builder": hub.chosen(Role::Builder),
            "worker": hub.chosen(Role::Worker),
            "reviewer": hub.chosen(Role::Reviewer),
            "extractor": hub.chosen(Role::Extractor),
        }
    }))
}

#[derive(Deserialize)]
struct AndroidBackendInput {
    name: String,
    kind: BackendKind,
    model: String,
    #[serde(default)]
    base_url: Option<String>,
    #[serde(default)]
    key: Option<String>,
}

fn add_android_backend(hub: &AiHub, json: &str) -> Result<(), String> {
    let input: AndroidBackendInput = serde_json::from_str(json).map_err(|error| format!("Invalid provider settings: {error}"))?;
    let name = input.name.trim();
    let model = input.model.trim();
    if name.is_empty() {
        return Err("Give this provider a name.".to_string());
    }
    if model.is_empty() {
        return Err("Enter a model ID.".to_string());
    }
    if input.kind == BackendKind::AgentCli {
        return Err("Desktop command-line agents cannot run inside the Android app.".to_string());
    }
    let base_url = input.base_url.map(|value| value.trim().to_string()).filter(|value| !value.is_empty());
    if input.kind == BackendKind::OpenaiCompatible && base_url.is_none() {
        return Err("Enter the server URL, including /v1, for an OpenAI-compatible provider.".to_string());
    }
    let lead_missing = hub.chosen(Role::Lead).is_none();
    let builder_missing = hub.chosen(Role::Builder).is_none();
    let config = BackendConfig { name: name.to_string(), kind: input.kind, model: model.to_string(), base_url, ssh_host: None, enabled: true };
    hub.add_backend(config, input.key).map_err(|error| error.message)?;
    if lead_missing {
        hub.set_role(Role::Lead, Some(name)).map_err(|error| error.message)?;
    }
    if builder_missing {
        hub.set_role(Role::Builder, Some(name)).map_err(|error| error.message)?;
    }
    Ok(())
}

fn set_android_role(hub: &AiHub, role: &str, name: &str) -> Result<(), String> {
    let role = match role {
        "lead" => Role::Lead,
        "builder" => Role::Builder,
        "worker" => Role::Worker,
        "reviewer" => Role::Reviewer,
        "extractor" => Role::Extractor,
        _ => return Err(format!("Unknown AI role: {role}")),
    };
    hub.set_role(role, (!name.trim().is_empty()).then_some(name.trim())).map_err(|error| error.message)
}

/// Serialize the tools registered by the engine for the native Android browser.
fn tool_catalog_json(engine: &Engine) -> String {
    let tools: Vec<_> = engine
        .registry()
        .all()
        .iter()
        .map(|tool| {
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
        })
        .collect();

    serde_json::to_string(&tools).unwrap_or_else(|error| {
        log::error!("couldn't serialize the Android tool catalog: {error}");
        "[]".to_string()
    })
}

/// Dispatch a registered engine tool and return a stable JSON envelope.
fn dispatch_tool(engine: &Engine, tool_id: &str, params_json: &str, consent_granted: bool) -> String {
    let result = (|| -> Result<Value, String> {
        let params: Value = serde_json::from_str(params_json).map_err(|error| format!("Invalid tool parameters: {error}"))?;
        let consent = if consent_granted { Consent::Granted } else { Consent::NotAsked };
        let options = CallOptions::new(Caller::Ui).with_consent(consent);
        let output = engine.call(tool_id, params, options).map_err(|error| error.message)?;
        Ok(serde_json::json!({ "ok": true, "output": output.to_json() }))
    })();

    match result {
        Ok(value) => value.to_string(),
        Err(message) => serde_json::json!({ "ok": false, "error": message }).to_string(),
    }
}

// Keep the JNI ABI naming in jni-rs' audited attribute macro. This avoids hand-written
// unsafe symbol attributes and lets the bridge crate forbid unsafe code outright.
mod jni_exports {
    use super::{add_android_backend, ai_chat, ai_error, ai_hub, ai_ok, ai_status_json, dispatch_tool, engine, set_android_role, tool_catalog_json};
    use jni::errors::ThrowRuntimeExAndDefault;
    use jni::objects::{JObject, JString};
    use jni::sys::{jboolean, jstring};
    use jni::{EnvUnowned, jni_mangle};

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiStatus")]
    pub fn native_ai_status<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let response = match ai_hub() {
                    Ok(hub) => ai_status_json(hub),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiAddBackend")]
    pub fn native_ai_add_backend<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, input: JString<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let input = input.try_to_string(env)?;
                let response = match ai_hub().and_then(|hub| add_android_backend(hub, &input)) {
                    Ok(()) => serde_json::json!({ "ok": true }).to_string(),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiSetBackendKey")]
    pub fn native_ai_set_backend_key<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _this: JObject<'frame>,
        name: JString<'frame>,
        key: JString<'frame>,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let name = name.try_to_string(env)?;
                let key = key.try_to_string(env)?;
                let response = match ai_hub().and_then(|hub| hub.set_backend_key(&name, &key).map_err(|error| error.message)) {
                    Ok(()) => serde_json::json!({ "ok": true }).to_string(),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiSetRole")]
    pub fn native_ai_set_role<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, role: JString<'frame>, name: JString<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let role = role.try_to_string(env)?;
                let name = name.try_to_string(env)?;
                let response = match ai_hub().and_then(|hub| set_android_role(hub, &role, &name)) {
                    Ok(()) => serde_json::json!({ "ok": true }).to_string(),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiSetEnabled")]
    pub fn native_ai_set_enabled<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, name: JString<'frame>, enabled: jboolean) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let name = name.try_to_string(env)?;
                let response = match ai_hub().and_then(|hub| hub.set_enabled(&name, enabled).map_err(|error| error.message)) {
                    Ok(changed) => ai_ok(serde_json::json!({ "changed": changed })),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiRemoveBackend")]
    pub fn native_ai_remove_backend<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, name: JString<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let name = name.try_to_string(env)?;
                let response = match ai_hub().and_then(|hub| hub.remove_backend(&name).map_err(|error| error.message)) {
                    Ok(removed) => ai_ok(serde_json::json!({ "removed": removed })),
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiStartChat")]
    pub fn native_ai_start_chat<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, backend: JString<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let backend = backend.try_to_string(env)?;
                let started = ai_hub().and_then(|hub| hub.start((!backend.trim().is_empty()).then_some(backend.as_str()), true).map_err(|error| error.message));
                let response = match started {
                    Ok(conversation) => {
                        let mut slot = ai_chat().lock().unwrap_or_else(std::sync::PoisonError::into_inner);
                        if let Some(previous) = slot.as_mut() {
                            previous.stop();
                        }
                        *slot = Some(conversation);
                        serde_json::json!({ "ok": true }).to_string()
                    }
                    Err(error) => ai_error(error),
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiSendChatMessage")]
    pub fn native_ai_send_chat_message<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, message: JString<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let message = message.try_to_string(env)?;
                let response = {
                    let mut slot = ai_chat().lock().unwrap_or_else(std::sync::PoisonError::into_inner);
                    match slot.as_mut() {
                        Some(conversation) if !message.trim().is_empty() => {
                            conversation.send(&message);
                            serde_json::json!({ "ok": true }).to_string()
                        }
                        Some(_) => ai_error("Type a message first."),
                        None => ai_error("Start an AI conversation before sending a message."),
                    }
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiPollChatEvents")]
    pub fn native_ai_poll_chat_events<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let response = {
                    let mut slot = ai_chat().lock().unwrap_or_else(std::sync::PoisonError::into_inner);
                    match slot.as_mut() {
                        Some(conversation) => {
                            let events = conversation.poll();
                            match serde_json::to_value(events) {
                                Ok(events) => ai_ok(serde_json::json!({ "events": events, "busy": conversation.is_busy() })),
                                Err(error) => ai_error(format!("Couldn't read AI events: {error}")),
                            }
                        }
                        None => ai_error("There is no active AI conversation."),
                    }
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiAnswerConsent")]
    pub fn native_ai_answer_consent<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, call_id: JString<'frame>, allow: jboolean) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let call_id = call_id.try_to_string(env)?;
                let response = {
                    let mut slot = ai_chat().lock().unwrap_or_else(std::sync::PoisonError::into_inner);
                    match slot.as_mut() {
                        Some(conversation) => {
                            conversation.answer_consent(&call_id, allow);
                            serde_json::json!({ "ok": true }).to_string()
                        }
                        None => ai_error("There is no active AI conversation."),
                    }
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeAiStopChat")]
    pub fn native_ai_stop_chat<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let response = {
                    let mut slot = ai_chat().lock().unwrap_or_else(std::sync::PoisonError::into_inner);
                    if let Some(conversation) = slot.as_mut() {
                        conversation.stop();
                    }
                    *slot = None;
                    serde_json::json!({ "ok": true }).to_string()
                };
                Ok(env.new_string(response)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeInitialize")]
    pub fn native_initialize<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>, app_data_dir: JString<'frame>) -> jstring {
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

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeVersion")]
    pub fn native_version<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> { Ok(env.new_string(env!("CARGO_PKG_VERSION"))?.into_raw()) })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeToolCatalog")]
    pub fn native_tool_catalog<'frame>(mut unowned_env: EnvUnowned<'frame>, _this: JObject<'frame>) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let catalog = match engine() {
                    Ok(engine) => tool_catalog_json(engine),
                    Err(error) => {
                        log::error!("couldn't read the Android tool catalog: {error}");
                        "[]".to_string()
                    }
                };
                Ok(env.new_string(catalog)?.into_raw())
            })
            .resolve::<ThrowRuntimeExAndDefault>()
    }

    /// Dispatch one registered engine tool. Kotlin must call this away from the UI thread.
    #[jni_mangle("ai.storytold.craftanything.MainActivity", "nativeCallTool")]
    pub fn native_call_tool<'frame>(
        mut unowned_env: EnvUnowned<'frame>,
        _this: JObject<'frame>,
        tool_id: JString<'frame>,
        params_json: JString<'frame>,
        consent_granted: jboolean,
    ) -> jstring {
        unowned_env
            .with_env(|env| -> jni::errors::Result<jstring> {
                let tool_id = tool_id.try_to_string(env)?;
                let params_text = params_json.try_to_string(env)?;
                let response = match engine() {
                    Ok(engine) => dispatch_tool(engine, &tool_id, &params_text, consent_granted),
                    Err(error) => serde_json::json!({ "ok": false, "error": error }).to_string(),
                };
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
    fn engine_requires_explicit_initialization() {
        assert!(engine().is_err(), "tool access must wait for app-specific settings");
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
    fn project_list_uses_the_android_private_projects_directory() {
        let root = std::env::temp_dir().join(format!("craft-anything-android-projects-{}", std::process::id()));
        let path = root.to_string_lossy().into_owned();
        let settings = settings_for_app_data_dir(&path);
        assert!(settings.is_ok(), "a valid app directory should configure successfully");
        if let Ok(settings) = settings {
            let projects_dir = settings.projects_dir.clone();
            let configured_engine = Engine::new(Session::new(settings));
            let response = dispatch_tool(&configured_engine, "project_list", "{}", false);
            let value = serde_json::from_str::<Value>(&response).unwrap_or(Value::Null);
            assert_eq!(value["ok"], Value::Bool(true));
            let expected_dir = projects_dir.map(|directory| directory.display().to_string()).unwrap_or_default();
            assert_eq!(value["output"]["result"]["projects_dir"].as_str(), Some(expected_dir.as_str()));
        }
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn ai_settings_keep_keys_private_and_report_roles() {
        let root = std::env::temp_dir().join(format!("craft-anything-android-ai-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&root);
        let hub = AiHub::with_stores(Arc::new(test_engine()), root.join("settings.toml"), KeyStore::file_only(root.join("ai-keys.toml")));
        let added = hub.add_backend(
            BackendConfig {
                name: "Test Gemini".to_string(),
                kind: BackendKind::Gemini,
                model: "gemini-test".to_string(),
                base_url: None,
                ssh_host: None,
                enabled: true,
            },
            Some("private-test-key".to_string()),
        );
        assert!(added.is_ok(), "a provider and its key should be saved");
        assert!(hub.set_role(Role::Lead, Some("Test Gemini")).is_ok());
        let status = ai_status_json(&hub);
        let parsed = serde_json::from_str::<Value>(&status).unwrap_or(Value::Null);
        assert_eq!(parsed["ok"], Value::Bool(true));
        assert_eq!(parsed["output"]["roles"]["lead"], Value::String("Test Gemini".to_string()));
        assert_eq!(parsed["output"]["backends"][0]["has_key"], Value::Bool(true));
        assert!(!status.contains("private-test-key"), "the provider key must never be returned to Android");
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn the_shared_android_engine_registers_rebuild_controls() {
        let session = Session::isolated(&std::env::temp_dir());
        let engine = Engine::shared(session, craft_anything_ai::tools());
        let catalog = tool_catalog_json(&engine);
        let value = serde_json::from_str::<Value>(&catalog).unwrap_or(Value::Null);
        for id in ["rebuild_start", "rebuild_status", "rebuild_stop"] {
            assert!(value.as_array().is_some_and(|items| items.iter().any(|item| item["id"] == id)), "the shared Android engine must expose {id}");
        }
    }

    #[test]
    fn catalogue_includes_the_file_open_tool() {
        let catalog = tool_catalog_json(&test_engine());
        let value = serde_json::from_str::<Value>(&catalog).unwrap_or(Value::Null);
        let includes_open = value.as_array().is_some_and(|items| items.iter().any(|item| item["id"] == "core_target_open"));
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
        assert!(items.iter().any(|item| item["requires_consent"] == Value::Bool(true)), "the registry should expose at least one consent-gated tool");
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
