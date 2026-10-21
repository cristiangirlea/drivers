use crate::BRIDGE;
use napi::bindgen_prelude::*;
use napi::threadsafe_function::ThreadsafeFunctionCallMode;
use napi_derive::napi;
use sf_core::logging::{CallbackLayer, LogManager, LoggingConfig, NormalizedEvent};
use sf_core::telemetry::snowflake_exporter::SessionRegistry;
use std::sync::atomic::{AtomicI32, Ordering};
use std::sync::{Arc, OnceLock};

type JsLogSink = Box<dyn Fn(LogEvent) + Send + Sync>;

const INFO_LEVEL: i32 = 2;

pub(crate) struct JsLogging {
    sink: Arc<OnceLock<JsLogSink>>,
    level: Arc<AtomicI32>,
}

impl Default for JsLogging {
    fn default() -> Self {
        Self {
            sink: Arc::default(),
            level: Arc::new(AtomicI32::new(INFO_LEVEL)),
        }
    }
}

impl JsLogging {
    pub(crate) fn log_manager(&self) -> Option<LogManager> {
        let sink = Arc::clone(&self.sink);
        let level = Arc::clone(&self.level);
        let layer = CallbackLayer::new(move |event| {
            if let Some(send) = sink.get()
                && i64::from(event.level) <= i64::from(level.load(Ordering::Relaxed))
            {
                send(event.into());
            }
        });
        let log_manager =
            LogManager::with_app_sink(LoggingConfig::default(), layer, SessionRegistry::default())
                .inspect_err(|e| eprintln!("snowflake-sdk: logging disabled: {e:?}"))
                .ok()?;
        if let Err(e) = tracing::dispatcher::set_global_default(log_manager.dispatch().clone()) {
            eprintln!("snowflake-sdk: log dispatcher not installed: {e}");
        }
        Some(log_manager)
    }
}

#[napi(object)]
pub struct LogEvent {
    pub level: u32,
    pub message: String,
}

impl From<NormalizedEvent> for LogEvent {
    fn from(event: NormalizedEvent) -> Self {
        Self {
            level: event.level,
            message: event.message,
        }
    }
}

pub(crate) fn init(log_callback: Function<'static, LogEvent, ()>) -> Result<()> {
    let tsfn = log_callback
        .build_threadsafe_function::<LogEvent>()
        .weak::<true>()
        .build()?;
    let _ = BRIDGE.logging.sink.set(Box::new(move |event| {
        let _ = tsfn.call(event, ThreadsafeFunctionCallMode::NonBlocking);
    }));
    Ok(())
}

#[napi]
pub fn set_log_level(level: i32) {
    BRIDGE.logging.level.store(level, Ordering::Relaxed);
}

#[napi]
pub fn log_event(level: u32, message: String) {
    sf_core::wrapper_event!(level, message = message.as_str());
}
