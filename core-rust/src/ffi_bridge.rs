use crate::signal_dsp::{MusicModeDspConfig, SignalDspProcessor};
use crate::transport::{compute_crc32, ServiceChannel, TransportMultiplexer};

/// Selecciona el medio de transporte óptimo vía FFI respetando USB (1) -> Wi-Fi (2) -> Bluetooth (3), o 0 si ninguno está disponible.
#[no_mangle]
pub extern "C" fn upiicsa_select_transport_medium(
    usb_available: u8,
    wifi_available: u8,
    bluetooth_available: u8,
) -> u8 {
    let mux = TransportMultiplexer::new();
    match mux.select_best_medium(
        usb_available != 0,
        wifi_available != 0,
        bluetooth_available != 0,
    ) {
        Some(medium) => medium as u8,
        None => 0,
    }
}

/// Verifica la disponibilidad simulada de un dispositivo Android por cable USB desde la capa nativa FFI.
#[no_mangle]
pub extern "C" fn upiicsa_probe_usb_device(simulate_usb_attached: u8) -> u8 {
    // [DUMMY-PRUEBAS]: Retorna 1 de forma determinista según bandera hasta integrar detección USB AOAv2/ADB real en Fase 3.
    if simulate_usb_attached != 0 {
        1
    } else {
        0
    }
}

/// Procesa un arreglo de muestras PCM i16 de Music Mode vía puntero FFI y retorna la cantidad de muestras recortadas por el limitador.
#[no_mangle]
pub unsafe extern "C" fn upiicsa_process_music_mode_pcm(
    samples_ptr: *mut i16,
    sample_count: usize,
    gain_multiplier: f32,
) -> i32 {
    if samples_ptr.is_null() || sample_count == 0 {
        return -1;
    }
    let slice = unsafe { std::slice::from_raw_parts_mut(samples_ptr, sample_count) };
    let mut config = MusicModeDspConfig::default();
    if gain_multiplier > 0.0 && gain_multiplier <= 3.0 {
        config.gain_multiplier = gain_multiplier;
    }
    let processor = SignalDspProcessor::new(config);
    let telemetry = processor.process_music_mode_pcm_block(slice);
    telemetry.clipped_samples as i32
}

/// Calcula el checksum CRC32 de una carga útil vía FFI para validar integridad entre Kotlin y Rust.
#[no_mangle]
pub unsafe extern "C" fn upiicsa_compute_frame_crc32(
    payload_ptr: *const u8,
    payload_len: usize,
    channel_id: u8,
    sequence_id: u32,
) -> u32 {
    if payload_ptr.is_null() && payload_len > 0 {
        return 0;
    }
    let payload = if payload_len == 0 {
        &[]
    } else {
        unsafe { std::slice::from_raw_parts(payload_ptr, payload_len) }
    };
    let channel = match channel_id {
        1 => ServiceChannel::NotificationResolution,
        2 => ServiceChannel::ClipboardSync,
        3 => ServiceChannel::SecondaryDisplayVideo,
        4 => ServiceChannel::WebcamMicStream,
        _ => ServiceChannel::MusicModeAudio,
    };
    compute_crc32(payload, channel as u8, sequence_id)
}
