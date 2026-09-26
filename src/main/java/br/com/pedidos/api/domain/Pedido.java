package br.com.pedidos.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Pedido(UUID id, String clienteId, StatusPedido status, List<ItemPedido> itens) {

	private static final BigDecimal TOTAL_VAZIO = BigDecimal.ZERO.setScale(ItemPedido.ESCALA);

	public Pedido {
		Objects.requireNonNull(id, "id é obrigatório");
		Objects.requireNonNull(clienteId, "clienteId é obrigatório");
		Objects.requireNonNull(status, "status é obrigatório");
		Objects.requireNonNull(itens, "itens é obrigatório");
		if (clienteId.isBlank()) {
			throw new IllegalArgumentException("clienteId não pode estar em branco");
		}
		itens = List.copyOf(itens);
	}

	public static Pedido novo(String clienteId) {
		return new Pedido(UUID.randomUUID(), clienteId, StatusPedido.ABERTO, List.of());
	}

	public Pedido adicionarItem(ItemPedido item) {
		Objects.requireNonNull(item, "item é obrigatório");
		exigirAberto("adicionar item");
		List<ItemPedido> novosItens = new ArrayList<>(itens);
		novosItens.add(item);
		return new Pedido(id, clienteId, status, novosItens);
	}

	public Pedido pagar() {
		exigirAberto("pagar");
		return new Pedido(id, clienteId, StatusPedido.PAGO, itens);
	}

	public Pedido cancelar() {
		exigirAberto("cancelar");
		return new Pedido(id, clienteId, StatusPedido.CANCELADO, itens);
	}

	public BigDecimal total() {
		return itens.stream()
				.map(ItemPedido::subtotal)
				.reduce(TOTAL_VAZIO, BigDecimal::add);
	}

	private void exigirAberto(String operacao) {
		if (status != StatusPedido.ABERTO) {
			throw new PedidoFechadoException("Não é possível " + operacao + " em pedido " + status);
		}
	}

}
