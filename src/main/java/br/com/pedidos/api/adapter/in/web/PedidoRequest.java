package br.com.pedidos.api.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do POST /pedidos. As anotações conferem só formato e presença.
 * Quantidade e preço positivos são regra do domínio, e lista vazia é regra do caso de uso.
 */
public record PedidoRequest(
		@NotBlank String clienteId,
		@NotNull List<@NotNull @Valid Item> itens) {

	public record Item(
			@NotBlank String sku,
			@NotNull Integer quantidade,
			@NotNull BigDecimal precoUnitario) {
	}

}
