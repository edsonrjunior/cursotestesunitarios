package br.ce.wcaquino.servicos;

import static br.ce.wcaquino.builders.FilmeBuilder.umFilme;
import static br.ce.wcaquino.builders.FilmeBuilder.umFilmeSemEstoque;
import static br.ce.wcaquino.builders.LocacaoBuilder.umLocacao;
import static br.ce.wcaquino.builders.UsuarioBuilder.umUsuario;
import static br.ce.wcaquino.matchers.MatchersProprios.caiNumaSegunda;
import static br.ce.wcaquino.matchers.MatchersProprios.ehHoje;
import static br.ce.wcaquino.matchers.MatchersProprios.ehHojeComDiferencaDias;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import br.ce.wcaquino.builders.UsuarioBuilder;
import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;

public class LocacaoServiceTest {
	@InjectMocks
	private LocacaoService locacaoService;
	@Mock
	private SPCService spcService;
	@Mock
	private EmailService emailService;
	@Mock
	private LocacaoDAO locacaoDAO;

	@Rule
	public ErrorCollector error = new ErrorCollector();

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@Before
	public void setup() {
		openMocks(this);
	}

	@Test
	public void deveAlugarFilme() throws Exception {
		Assume.assumeFalse(DataUtils.verificarDiaSemana(new Date(), Calendar.SATURDAY));

		// cenario
		Usuario usuario = umUsuario().agora();
		List<Filme> filmes = List.of(umFilme().comValor(5.0).agora());

		// acao
		Locacao locacao = locacaoService.alugarFilme(usuario, filmes);

		// verificacao
		error.checkThat(locacao.getValor(), is(equalTo(5.0)));
		error.checkThat(locacao.getDataLocacao(), ehHoje());
		error.checkThat(locacao.getDataRetorno(), ehHojeComDiferencaDias(1));
	}

	@Test(expected = FilmeSemEstoqueException.class)
	public void naoDeveAlugarFilmeSemEstoque() throws Exception {
		// cenario
		Usuario usuario = umUsuario().agora();
		List<Filme> filmes = List.of(umFilmeSemEstoque().agora());

		// acao
		locacaoService.alugarFilme(usuario, filmes);
	}

	@Test
	public void naoDeveAlugarFilmeSemUsuario() throws Exception {
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
	public void naoDeveAlugarFilmeSemFilme() throws Exception {
		// cenario
		Usuario usuario = umUsuario().agora();

		exception.expect(LocadoraException.class);
		exception.expectMessage("Filme vazio");

		// acao
		locacaoService.alugarFilme(usuario, null);

	}

	@Test
	public void deveDevolverNaSegundaAoAlugarNoSabado() throws Exception {
		Assume.assumeTrue(DataUtils.verificarDiaSemana(new Date(), Calendar.SATURDAY));

		// cenario
		Usuario usuario = umUsuario().agora();
		List<Filme> filmes = List.of(umFilme().agora());

		// acao
		Locacao retorno = locacaoService.alugarFilme(usuario, filmes);

		// verificacao
		assertThat(retorno.getDataRetorno(), caiNumaSegunda());

	}

	@Test
	public void naoDeveAlugarFilmeParaNegativadoSPC() throws Exception {
		// Cenario
		Usuario usuario = UsuarioBuilder.umUsuario().agora();
		Usuario usuario2 = UsuarioBuilder.umUsuario().comNome("Edson").agora();
		List<Filme> filmes = List.of(umFilme().agora());

		// Alterando o comportamento da chamada
		when(spcService.pussuiNegativacao(usuario)).thenReturn(true);
		// ou
		// when(spcService.pussuiNegativacao(any(Usuario.class))).thenReturn(true);

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
	public void enviarEmailParaLocacoesAtrasadas() {
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
	public void deveTratarErroNoSPC() throws Exception {
		// cenario
		Usuario usuario1 = UsuarioBuilder.umUsuario().agora();
		List<Filme> listaDeFilmes = Arrays.asList(umFilme().agora());

		when(spcService.pussuiNegativacao(usuario1)).thenThrow(new Exception("Falha catastrofica"));

		// verificacao
		exception.expect(LocadoraException.class);
		exception.expectMessage("Problemas com o SPC, tente novamente");

		// acao
		locacaoService.alugarFilme(usuario1, listaDeFilmes);

	}

	@Test
	public void deveProrrogarUmaLocacao() {
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
