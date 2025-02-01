package br.ce.wcaquino.servicos;

import br.ce.wcaquino.builders.UsuarioBuilder;
import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.*;

import static br.ce.wcaquino.builders.FilmeBuilder.umFilme;
import static br.ce.wcaquino.builders.FilmeBuilder.umFilmeSemEstoque;
import static br.ce.wcaquino.builders.LocacaoBuilder.umLocacao;
import static br.ce.wcaquino.builders.UsuarioBuilder.umUsuario;
import static br.ce.wcaquino.matchers.MatchersProprios.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

class LocacaoServiceTest {
    @InjectMocks
    private LocacaoService locacaoService;
    @Mock
    private SPCService spcService;
    @Mock
    private EmailService emailService;
    @Mock
    private LocacaoDAO locacaoDAO;

    @BeforeEach
    void setup() {
        openMocks(this);
    }

    @Test
    void deveAlugarFilme() throws Exception {
        Assumptions.assumeFalse(DataUtils.verificarDiaSemana(new Date(), Calendar.SATURDAY));

        // cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = List.of(umFilme().comValor(5.0).agora());

        // acao
        Locacao locacao = locacaoService.alugarFilme(usuario, filmes);

        // verificacao
        assertEquals(5.0, locacao.getValor());

        assertThat(locacao.getDataLocacao(), ehHoje());
        assertThat(locacao.getDataRetorno(), ehHojeComDiferencaDias(1));
    }

    @Test
    void naoDeveAlugarFilmeSemEstoque() {
        // cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = List.of(umFilmeSemEstoque().agora());

        assertThrows(FilmeSemEstoqueException.class, () ->
            // acao e validacao
            locacaoService.alugarFilme(usuario, filmes)
        );
    }

    @Test
    void naoDeveAlugarFilmeSemUsuario() throws Exception {
        // cenario
        List<Filme> filmes = List.of(umFilme().agora());

        // acao
        try {
            locacaoService.alugarFilme(null, filmes);
            fail();
        } catch (LocadoraException e) {
            assertEquals("Usuario vazio", e.getMessage());
        }
    }

    @Test
    void naoDeveAlugarFilmeSemFilme() {
        // cenario
        Usuario usuario = umUsuario().agora();

        Exception exception = assertThrows(LocadoraException.class, () ->
            // acao e validacao
            locacaoService.alugarFilme(usuario, null)
        );

        assertEquals("Filme vazio", exception.getMessage());
    }

    @Test
    void deveDevolverNaSegundaAoAlugarNoSabado() throws Exception {
        Assumptions.assumeTrue(DataUtils.verificarDiaSemana(new Date(), Calendar.SATURDAY));

        // cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = List.of(umFilme().agora());

        // acao
        Locacao retorno = locacaoService.alugarFilme(usuario, filmes);

        // verificacao
        assertThat(retorno.getDataRetorno(), caiNumaSegunda());
    }

    @Test
    void naoDeveAlugarFilmeParaNegativadoSPC() throws Exception {
        // Cenario
        Usuario usuario = UsuarioBuilder.umUsuario().agora();
        List<Filme> filmes = List.of(umFilme().agora());

        // Alterando o comportamento da chamada
        when(spcService.pussuiNegativacao(any(Usuario.class))).thenReturn(true);

        // acao
        try {
            locacaoService.alugarFilme(usuario, filmes);
            fail(); // para não gerar falso positivo em caso de falha
        } catch (LocadoraException e) {
            assertEquals("Usuario negativado", e.getMessage());
        }

        // verificacao
        verify(spcService).pussuiNegativacao(usuario); // verificar se a chamada foi realizada para o usuario1
    }

    @Test
    void enviarEmailParaLocacoesAtrasadas() {
        // cenario
        Usuario usuario1 = UsuarioBuilder.umUsuario().agora();
        Usuario usuario2 = UsuarioBuilder.umUsuario().comNome("Usuario em dia").agora();
        Usuario usuario3 = UsuarioBuilder.umUsuario().comNome("Outro usuario atrasado").agora();

        List<Locacao> listaDeLocacoesPendentes = Arrays.asList(umLocacao().comUsuario(usuario1).atrasado().agora(),
                umLocacao().comUsuario(usuario2).agora(), umLocacao().comUsuario(usuario3).atrasado().agora(),
                umLocacao().comUsuario(usuario3).atrasado().agora());

        when(locacaoDAO.obterLocacoesPendentes()).thenReturn(listaDeLocacoesPendentes);

        // acao
        locacaoService.notificarAtrasos();

        // verificacao

        // Deixar generico para não distinguir entre usuario1, usuario2 ou usuario3
        verify(emailService, times(3)).notificarAtraso(any(Usuario.class));
        verify(emailService).notificarAtraso(usuario1);
        verify(emailService, times(2)).notificarAtraso(usuario3); // Informo quantas vezes deve ser executado
        verify(emailService, atLeastOnce()).notificarAtraso(usuario3); // notificarAtraso Deve ser executado ao menos
        // uma vez
        verify(emailService, never()).notificarAtraso(usuario2);
        verifyNoMoreInteractions(emailService); // garantir que nenhum outro email foi enviado
    }

    @Test
    void deveTratarErroNoSPC() throws Exception {
        // cenario
        Usuario usuario1 = UsuarioBuilder.umUsuario().agora();
        List<Filme> listaDeFilmes = Collections.singletonList(umFilme().agora());

        when(spcService.pussuiNegativacao(usuario1)).thenThrow(new Exception("Falha catastrofica"));

        Exception exception = assertThrows(Exception.class, () ->
            // acao e validacao
            locacaoService.alugarFilme(usuario1, listaDeFilmes)
        );

        assertEquals("Problemas com o SPC, tente novamente", exception.getMessage());
    }

    @Test
    void deveProrrogarUmaLocacao() {
        // cenario
        Locacao locacao = umLocacao().agora();

        // acao
        locacaoService.prorrogarLocacao(locacao, 3);

        // verificacao
        ArgumentCaptor<Locacao> locacaoCaptor = forClass(Locacao.class);
        verify(locacaoDAO).salvar(locacaoCaptor.capture());
        Locacao locacaoRetornada = locacaoCaptor.getValue();
        assertThat(locacaoRetornada.getValor(), is(12.0));

    }

}
