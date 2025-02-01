package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static br.ce.wcaquino.builders.FilmeBuilder.umFilme;
import static br.ce.wcaquino.builders.UsuarioBuilder.umUsuario;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.MockitoAnnotations.openMocks;

class CalculoValorLocacaoTest {

    @InjectMocks
    private LocacaoService service;

    @Mock
    private LocacaoDAO dao;

    @Mock
    private SPCService spcService;

    private static final Filme filme1 = umFilme().agora();
    private static final Filme filme2 = umFilme().agora();
    private static final Filme filme3 = umFilme().agora();
    private static final Filme filme4 = umFilme().agora();
    private static final Filme filme5 = umFilme().agora();
    private static final Filme filme6 = umFilme().agora();
    private static final Filme filme7 = umFilme().agora();

    @BeforeEach
    void setup() {
        openMocks(this);
    }

    static Stream<Arguments> getParametros() {
        return Stream.of(
                Arguments.of(Arrays.asList(filme1, filme2), 8.0, "2 Filmes: Sem Desconto"),
                Arguments.of(Arrays.asList(filme1, filme2, filme3), 11.0, "3 Filmes: 25%"),
                Arguments.of(Arrays.asList(filme1, filme2, filme3, filme4), 13.0, "4 Filmes: 50%"),
                Arguments.of(Arrays.asList(filme1, filme2, filme3, filme4, filme5), 14.0, "5 Filmes: 75%"),
                Arguments.of(Arrays.asList(filme1, filme2, filme3, filme4, filme5, filme6), 14.0, "6 Filmes: 100%"),
                Arguments.of(Arrays.asList(filme1, filme2, filme3, filme4, filme5, filme6, filme7), 18.0, "7 Filmes: Sem Desconto")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("getParametros")
    void deveCalcularValorLocacaoConsiderandoDescontos(List<Filme> filmes, Double valorLocacao, String cenario) throws Exception {
        // Cenário
        Usuario usuario = umUsuario().agora();

        // Ação
        Locacao resultado = service.alugarFilme(usuario, filmes);

        // Verificação
        assertEquals(valorLocacao, resultado.getValor());
    }
}
