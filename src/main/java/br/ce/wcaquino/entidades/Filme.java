package br.ce.wcaquino.entidades;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Filme {

    private String nome;
    private Integer estoque;
    private double precoLocacao;

}