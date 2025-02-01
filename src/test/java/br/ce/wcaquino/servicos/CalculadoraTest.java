package br.ce.wcaquino.servicos;

import br.ce.wcaquino.exceptions.NaoPodeDividirPorZeroException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadoraTest {

    private Calculadora calc;

    @BeforeEach
    void setup() {
        calc = new Calculadora();
    }

    @Test
    void deveSomarDoisValores() {
        //cenario
        int a = 5;
        int b = 3;

        //acao
        int resultado = calc.somar(a, b);

        //verificacao
        assertEquals(8, resultado);
    }

    @Test
    void deveSubtrairDoisValores() {
        //cenario
        int a = 8;
        int b = 5;

        //acao
        int resultado = calc.subtrair(a, b);

        //verificacao
        assertEquals(3, resultado);
    }

    @Test
    void deveDividirDoisValores() throws NaoPodeDividirPorZeroException {
        //cenario
        int a = 6;
        int b = 3;

        //acao
        int resultado = calc.divide(a, b);

        //verificacao
        assertEquals(2, resultado);
    }

    @Test
    void deveLancarExcecaoAoDividirPorZero()  {
        int a = 10;
        int b = 0;

        assertThrows(NaoPodeDividirPorZeroException.class, () ->
            calc.divide(a, b)
        );
    }

    @Test
    void deveDividir() {
        String a = "6";
        String b = "3";

        int resultado = calc.divide(a, b);

        assertEquals(2, resultado);
    }

}
