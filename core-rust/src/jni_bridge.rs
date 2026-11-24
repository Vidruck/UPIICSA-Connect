//! Puente JNI para Android: expone las mismas operaciones de `ffi_bridge` a la clase Kotlin
//! `mx.ipn.upiicsa.connect.android.model.nativecore.JniRustCoreBridge`.

use jni::objects::{JByteArray, JObject, JShortArray};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jlong};
use jni::JNIEnv;

use crate::ffi_bridge::{
    upiicsa_compute_frame_crc32, upiicsa_core_version, upiicsa_derive_pairing_pin,
    upiicsa_encode_frame, upiicsa_process_music_mode_pcm, upiicsa_select_transport_medium,
    upiicsa_validate_wire_frame,
};
use crate::transport::FRAME_HEADER_LEN;

/// Devuelve la versión empaquetada del core (`mayor << 16 | menor << 8 | parche`).
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeCoreVersion(
    _env: JNIEnv,
    _this: JObject,
) -> jint {
    upiicsa_core_version() as jint
}

/// Selecciona el medio de transporte prioritario; retorna 0 si ninguno está disponible.
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeSelectTransport(
    _env: JNIEnv,
    _this: JObject,
    usb: jboolean,
    wifi: jboolean,
    bluetooth: jboolean,
) -> jint {
    upiicsa_select_transport_medium(usb, wifi, bluetooth) as jint
}

/// Procesa en sitio el bloque PCM de Music Mode y retorna las muestras recortadas, o -1 ante error JNI.
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeProcessMusicModePcm<
    'local,
>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
    samples: JShortArray<'local>,
    gain: jfloat,
) -> jint {
    let Ok(length) = env.get_array_length(&samples) else {
        return -1;
    };
    let mut buffer = vec![0i16; length.max(0) as usize];
    if env
        .get_short_array_region(&samples, 0, &mut buffer)
        .is_err()
    {
        return -1;
    }
    let clipped =
        unsafe { upiicsa_process_music_mode_pcm(buffer.as_mut_ptr(), buffer.len(), gain) };
    if env.set_short_array_region(&samples, 0, &buffer).is_err() {
        return -1;
    }
    clipped
}

/// Calcula el CRC32 de la carga útil para el canal y secuencia indicados (sin signo en los 32 bits bajos).
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeComputeCrc32<
    'local,
>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
    payload: JByteArray<'local>,
    channel_id: jint,
    sequence_id: jlong,
) -> jlong {
    let Ok(bytes) = env.convert_byte_array(&payload) else {
        return 0;
    };
    let crc = unsafe {
        upiicsa_compute_frame_crc32(
            bytes.as_ptr(),
            bytes.len(),
            channel_id as u8,
            sequence_id as u32,
        )
    };
    jlong::from(crc)
}

/// Serializa una trama completa; retorna `null` si los identificadores o el tamaño son inválidos.
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeEncodeFrame<
    'local,
>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
    medium_id: jint,
    channel_id: jint,
    sequence_id: jlong,
    payload: JByteArray<'local>,
) -> jbyteArray {
    let Ok(bytes) = env.convert_byte_array(&payload) else {
        return std::ptr::null_mut();
    };
    let mut out = vec![0u8; FRAME_HEADER_LEN + bytes.len()];
    let written = unsafe {
        upiicsa_encode_frame(
            medium_id as u8,
            channel_id as u8,
            sequence_id as u32,
            bytes.as_ptr(),
            bytes.len(),
            out.as_mut_ptr(),
            out.len(),
        )
    };
    if written < 0 {
        return std::ptr::null_mut();
    }
    out.truncate(written as usize);
    env.byte_array_from_slice(&out)
        .map(JByteArray::into_raw)
        .unwrap_or(std::ptr::null_mut())
}

/// Valida una trama serializada: retorna el canal (1..6) o el código negativo de `WireFrameError`.
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeValidateWireFrame<
    'local,
>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
    frame: JByteArray<'local>,
) -> jint {
    let Ok(bytes) = env.convert_byte_array(&frame) else {
        return -1;
    };
    unsafe { upiicsa_validate_wire_frame(bytes.as_ptr(), bytes.len()) }
}

/// Deriva el PIN de emparejamiento a partir de los identificadores UTF-8; retorna -1 si no son válidos.
#[no_mangle]
pub extern "system" fn Java_mx_ipn_upiicsa_connect_android_model_nativecore_JniRustCoreBridge_nativeDerivePairingPin<
    'local,
>(
    env: JNIEnv<'local>,
    _this: JObject<'local>,
    desktop_id: JByteArray<'local>,
    mobile_id: JByteArray<'local>,
    nonce: jlong,
) -> jint {
    let (Ok(desktop), Ok(mobile)) = (
        env.convert_byte_array(&desktop_id),
        env.convert_byte_array(&mobile_id),
    ) else {
        return -1;
    };
    unsafe {
        upiicsa_derive_pairing_pin(
            desktop.as_ptr(),
            desktop.len(),
            mobile.as_ptr(),
            mobile.len(),
            nonce as u32,
        ) as jint
    }
}
