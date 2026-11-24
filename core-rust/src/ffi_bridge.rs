use crate::pairing::derive_pairing_pin;
use crate::signal_dsp::{MusicModeDspConfig, SignalDspProcessor};
use crate::transport::{
    build_frame, compute_crc32, ServiceChannel, TransportMedium, TransportMultiplexer,
    MAX_PAYLOAD_BYTES,
};

/// Código FFI: argumentos nulos o inválidos.
pub const FFI_ERR_INVALID_ARGS: i64 = -1;
/// Código FFI: el búfer de salida no tiene capacidad suficiente.
pub const FFI_ERR_BUFFER_TOO_SMALL: i64 = -2;
/// Código FFI: medio o canal desconocido.
pub const FFI_ERR_UNKNOWN_ID: i64 = -3;
/// Código FFI: la carga útil excede el máximo permitido.
pub const FFI_ERR_PAYLOAD_TOO_LARGE: i64 = -4;

/// Devuelve la versión del core empaquetada como `(mayor << 16) | (menor << 8) | parche` para verificar compatibilidad del binding.
#[no_mangle]
pub extern "C" fn upiicsa_core_version() -> u32 {
    let parse = |text: &str| text.parse::<u32>().unwrap_or(0) & 0xFF;
    (parse(env!("CARGO_PKG_VERSION_MAJOR")) << 16)
        | (parse(env!("CARGO_PKG_VERSION_MINOR")) << 8)
        | parse(env!("CARGO_PKG_VERSION_PATCH"))
}

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
///
/// # Safety
/// `samples_ptr` debe apuntar a `sample_count` muestras i16 válidas y mutables.
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
///
/// # Safety
/// `payload_ptr` debe apuntar a `payload_len` bytes legibles (puede ser nulo si `payload_len` es 0).
#[no_mangle]
pub unsafe extern "C" fn upiicsa_compute_frame_crc32(
    payload_ptr: *const u8,
    payload_len: usize,
    channel_id: u8,
    sequence_id: u32,
) -> u32 {
    let Some(payload) = (unsafe { borrow_bytes(payload_ptr, payload_len) }) else {
        return 0;
    };
    let channel = ServiceChannel::from_u8(channel_id).unwrap_or(ServiceChannel::MusicModeAudio);
    compute_crc32(payload, channel as u8, sequence_id)
}

/// Serializa una trama completa en el búfer provisto por Kotlin y retorna los bytes escritos o un código de error negativo.
///
/// # Safety
/// `payload_ptr` debe apuntar a `payload_len` bytes legibles y `out_ptr` a `out_capacity` bytes escribibles.
#[no_mangle]
pub unsafe extern "C" fn upiicsa_encode_frame(
    medium_id: u8,
    channel_id: u8,
    sequence_id: u32,
    payload_ptr: *const u8,
    payload_len: usize,
    out_ptr: *mut u8,
    out_capacity: usize,
) -> i64 {
    if out_ptr.is_null() {
        return FFI_ERR_INVALID_ARGS;
    }
    if payload_len > MAX_PAYLOAD_BYTES {
        return FFI_ERR_PAYLOAD_TOO_LARGE;
    }
    let Some(payload) = (unsafe { borrow_bytes(payload_ptr, payload_len) }) else {
        return FFI_ERR_INVALID_ARGS;
    };
    let (Some(medium), Some(channel)) = (
        TransportMedium::from_u8(medium_id),
        ServiceChannel::from_u8(channel_id),
    ) else {
        return FFI_ERR_UNKNOWN_ID;
    };
    let frame = build_frame(medium, channel, sequence_id, payload.to_vec());
    let encoded = TransportMultiplexer::encode_wire_bytes(&frame);
    if encoded.len() > out_capacity {
        return FFI_ERR_BUFFER_TOO_SMALL;
    }
    let out = unsafe { std::slice::from_raw_parts_mut(out_ptr, encoded.len()) };
    out.copy_from_slice(&encoded);
    encoded.len() as i64
}

/// Valida una trama serializada completa: retorna el canal lógico (1..6) si es válida o un código negativo de `WireFrameError`.
///
/// # Safety
/// `bytes_ptr` debe apuntar a `bytes_len` bytes legibles.
#[no_mangle]
pub unsafe extern "C" fn upiicsa_validate_wire_frame(
    bytes_ptr: *const u8,
    bytes_len: usize,
) -> i32 {
    let Some(bytes) = (unsafe { borrow_bytes(bytes_ptr, bytes_len) }) else {
        return -1;
    };
    match TransportMultiplexer::decode_wire_bytes(bytes) {
        Ok(frame) => frame.channel as i32,
        Err(error) => error.code(),
    }
}

/// Deriva el PIN de emparejamiento de 6 dígitos a partir de los identificadores UTF-8 de ambos equipos y un nonce.
///
/// # Safety
/// Ambos punteros deben apuntar a la cantidad indicada de bytes UTF-8 legibles.
#[no_mangle]
pub unsafe extern "C" fn upiicsa_derive_pairing_pin(
    desktop_id_ptr: *const u8,
    desktop_id_len: usize,
    mobile_id_ptr: *const u8,
    mobile_id_len: usize,
    nonce: u32,
) -> u32 {
    let desktop = unsafe { borrow_bytes(desktop_id_ptr, desktop_id_len) }
        .and_then(|b| std::str::from_utf8(b).ok());
    let mobile = unsafe { borrow_bytes(mobile_id_ptr, mobile_id_len) }
        .and_then(|b| std::str::from_utf8(b).ok());
    match (desktop, mobile) {
        (Some(d), Some(m)) => derive_pairing_pin(d, m, nonce),
        _ => u32::MAX,
    }
}

/// Convierte un puntero y longitud de C en un slice seguro; acepta puntero nulo solo con longitud cero.
///
/// # Safety
/// Si `ptr` no es nulo debe apuntar a `len` bytes legibles durante la vida del slice.
unsafe fn borrow_bytes<'a>(ptr: *const u8, len: usize) -> Option<&'a [u8]> {
    if len == 0 {
        return Some(&[]);
    }
    if ptr.is_null() {
        return None;
    }
    Some(unsafe { std::slice::from_raw_parts(ptr, len) })
}
