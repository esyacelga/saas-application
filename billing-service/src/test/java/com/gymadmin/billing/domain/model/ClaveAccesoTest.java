package com.gymadmin.billing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClaveAccesoTest {

    @Test
    void generarClaveAcceso_conDatosConocidos_debeProducirDigitoVerificadorCorrecto() {
        // fecha 09/12/2022 → "09122022"
        // tipo "01"
        // ruc  "1234567890001"
        // ambiente "1"
        // estab "001", ptoEmi "001"
        // secuencial "000000001"
        // codigoNum  "123456789"
        // primeros48 = "091220220112345678900011001001000000001123456789"
        ClaveAcceso clave = ClaveAcceso.generar(
                LocalDate.of(2022, 12, 9),
                "01",
                "1234567890001",
                "1",
                "001",
                "001",
                "000000001",
                "123456789"
        );

        assertEquals(49, clave.getValue().length());

        String primeros48 = "091220220112345678900011001001000000001123456789";
        assertEquals(primeros48, clave.getValue().substring(0, 48));

        // ClaveAcceso.of validates the check digit — must not throw
        assertDoesNotThrow(() -> ClaveAcceso.of(clave.getValue()));
    }

    @Test
    void generarClaveAcceso_digitoVerificador_esConsistenteConModulo11() {
        ClaveAcceso clave = ClaveAcceso.generar(
                LocalDate.of(2022, 12, 9),
                "01",
                "1234567890001",
                "1",
                "001",
                "001",
                "000000001",
                "123456789"
        );

        // Tampering the last digit must fail validation
        String tampered = clave.getValue().substring(0, 48) + ((Character.getNumericValue(clave.getValue().charAt(48)) + 1) % 10);
        assertThrows(IllegalArgumentException.class, () -> ClaveAcceso.of(tampered));
    }

    @Test
    void of_conClaveCorta_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> ClaveAcceso.of("1234567890"));
    }

    @Test
    void of_conClaveNula_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> ClaveAcceso.of(null));
    }

    @Test
    void of_conClaveConLetras_debeLanzarExcepcion() {
        String conLetras = "A".repeat(49);
        assertThrows(IllegalArgumentException.class, () -> ClaveAcceso.of(conLetras));
    }

    @Test
    void generar_conLongitudIncorrectaDeRuc_debeLanzarExcepcion() {
        // RUC de 10 dígitos en vez de 13 → los primeros 48 quedan mal
        assertThrows(IllegalArgumentException.class, () ->
                ClaveAcceso.generar(
                        LocalDate.of(2022, 12, 9),
                        "01",
                        "1234567890",    // solo 10 dígitos
                        "1",
                        "001",
                        "001",
                        "000000001",
                        "123456789"
                )
        );
    }

    @Test
    void equals_clavesMismoValor_sonIguales() {
        ClaveAcceso a = ClaveAcceso.generar(
                LocalDate.of(2022, 12, 9), "01", "1234567890001",
                "1", "001", "001", "000000001", "123456789");
        ClaveAcceso b = ClaveAcceso.of(a.getValue());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
