package br.com.pedidos.api.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.pedidos.api.domain.Pedido;

public record PedidoResponse(
		UUID id,
		String clienteId,
		List<Item> itens,
		String status,
		BigDecimal total) {

	public record Item(String sku, int quantidade, BigDecimal precoUnitario) {
	}

	static PedidoResponse de(Pedido pedido) {
		List<Item> itens = pedido.itens().stream()
				.map(item -> new Item(item.codigoProduto(), item.quantidade(), item.precoUnitario()))
				.toList();
		return new PedidoResponse(pedido.id(), pedido.clienteId(), itens, pedido.status().name(), pedido.total());
	}

}
