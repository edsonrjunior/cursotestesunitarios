package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.utils.DataUtils;
import lombok.RequiredArgsConstructor;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static br.ce.wcaquino.utils.DataUtils.adicionarDias;

@RequiredArgsConstructor
public class LocacaoService {

    private final LocacaoDAO locacaoDAO;
    private final SPCService spcService;
    private final EmailService emailService;

    public Locacao alugarFilme(Usuario usuario, List<Filme> filmes) throws Exception {
        if (usuario == null) {
            throw new LocadoraException("Usuario vazio");
        }

        if (Objects.isNull(filmes) || filmes.isEmpty()) {
            throw new LocadoraException("Filme vazio");
        }

        if(filmes.stream().anyMatch(filme -> filme.getEstoque().equals(0))){
            throw new FilmeSemEstoqueException();
        }

        boolean negativado;

        try {
            negativado = spcService.pussuiNegativacao(usuario);
        } catch (Exception e) {
            throw new LocadoraException("Problemas com o SPC, tente novamente");
        }

        if (negativado) {
            throw new LocadoraException("Usuario negativado");
        }

        Locacao locacao = new Locacao();
        locacao.setFilmes(filmes);
        locacao.setUsuario(usuario);
        locacao.setDataLocacao(new Date());

        double valorTotal = IntStream.range(0, filmes.size())
                .mapToDouble(i -> {
                    double valorFilme = filmes.get(i).getPrecoLocacao();
                    switch (i) {
                        case 2:
                            valorFilme *= 0.75;
                            break;
                        case 3:
                            valorFilme *= 0.5;
                            break;
                        case 4:
                            valorFilme *= 0.25;
                            break;
                        case 5:
                            valorFilme = 0d;
                            break;
                    }
                    return valorFilme;

                }).sum();

        locacao.setValor(valorTotal);

        //Entrega no dia seguinte
        Date dataEntrega = new Date();
        dataEntrega = adicionarDias(dataEntrega, 1);

        if (DataUtils.verificarDiaSemana(dataEntrega, Calendar.SUNDAY)) {
            dataEntrega = adicionarDias(dataEntrega, 1);
        }

        locacao.setDataRetorno(dataEntrega);

        //Salvando a locacao...
        locacaoDAO.salvar(locacao);

        return locacao;
    }

    public void notificarAtrasos() {
        List<Locacao> locacoes = locacaoDAO.obterLocacoesPendentes();

        locacoes.forEach(locacao -> {
            if (locacao.getDataRetorno().before(new Date()))
                emailService.notificarAtraso(locacao.getUsuario());
        });
    }

    public void prorrogarLocacao(Locacao locacao, int dias) {
        Locacao novaLocacao = new Locacao();
        novaLocacao.setUsuario(locacao.getUsuario());
        novaLocacao.setFilmes(locacao.getFilmes());
        novaLocacao.setDataLocacao(new Date());
        novaLocacao.setDataRetorno(DataUtils.obterDataComDiferencaDias(dias));
        novaLocacao.setValor(locacao.getValor() * dias);
        locacaoDAO.salvar(novaLocacao);
    }
}