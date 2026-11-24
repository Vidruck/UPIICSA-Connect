use crate::transport::compute_crc32;

/// Cantidad de combinaciones posibles de un PIN de emparejamiento de 6 dígitos.
pub const PIN_SPACE: u32 = 1_000_000;

/// Deriva el PIN de 6 dígitos que ambos equipos muestran para confirmar el emparejamiento, sin importar quién inicia.
pub fn derive_pairing_pin(desktop_id: &str, mobile_id: &str, nonce: u32) -> u32 {
    // [DUMMY-PRUEBAS]: Derivación determinista no criptográfica (CRC32 de identificadores ordenados + nonce) hasta implementar mTLS/ECDH en Fase 3.
    let (first, second) = if desktop_id <= mobile_id {
        (desktop_id, mobile_id)
    } else {
        (mobile_id, desktop_id)
    };
    let mut material = Vec::with_capacity(first.len() + second.len() + 1);
    material.extend_from_slice(first.as_bytes());
    material.push(0x1F);
    material.extend_from_slice(second.as_bytes());
    compute_crc32(&material, 0x50, nonce) % PIN_SPACE
}

/// Verifica que el PIN capturado por el usuario coincida con el derivado para el par de dispositivos.
pub fn verify_pairing_pin(desktop_id: &str, mobile_id: &str, nonce: u32, entered_pin: u32) -> bool {
    entered_pin < PIN_SPACE && derive_pairing_pin(desktop_id, mobile_id, nonce) == entered_pin
}

/// Formatea el PIN como dos grupos de tres dígitos (por ejemplo, "048 213") para mostrarlo en pantalla.
pub fn format_pairing_pin(pin: u32) -> String {
    let normalized = pin % PIN_SPACE;
    format!("{:03} {:03}", normalized / 1_000, normalized % 1_000)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Verifica que el PIN sea simétrico respecto del iniciador y dependa del nonce.
    #[test]
    fn prueba_pin_simetrico_y_dependiente_de_nonce() {
        let a = derive_pairing_pin("pc-linux-upiicsa", "usb-pixel8-upiicsa", 42);
        let b = derive_pairing_pin("usb-pixel8-upiicsa", "pc-linux-upiicsa", 42);
        assert_eq!(a, b);
        assert!(a < PIN_SPACE);
        assert_ne!(
            a,
            derive_pairing_pin("pc-linux-upiicsa", "usb-pixel8-upiicsa", 43)
        );
        assert!(verify_pairing_pin(
            "pc-linux-upiicsa",
            "usb-pixel8-upiicsa",
            42,
            a
        ));
        assert!(!verify_pairing_pin(
            "pc-linux-upiicsa",
            "usb-pixel8-upiicsa",
            42,
            PIN_SPACE + a
        ));
    }

    /// Verifica el formato visual del PIN con ceros a la izquierda.
    #[test]
    fn prueba_formato_de_pin() {
        assert_eq!(format_pairing_pin(48_213), "048 213");
        assert_eq!(format_pairing_pin(7), "000 007");
    }
}
