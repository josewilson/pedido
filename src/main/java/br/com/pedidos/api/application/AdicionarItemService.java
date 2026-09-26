package br.com.pedidos.api.application;

import java.util.Objects;
import java.util.UUID;

import br.com.pedidos.api.application.port.in.AdicionarItem;
import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

public class AdicionarItemService implements AdicionarItem {

	private final Pedidos pedidos;

	public AdicionarItemService(Pedidos pedidos) {
		this.pedidos = Objects.requireNonNull(pedidos, "pedidos é obrigatório");
	}

	@Override
	public Pedido adicionar(UUID pedidoId, ItemPedido item) {
		Objects.requireNonNull(pedidoId, "pedidoId é obrigatório");
		Objects.requireNonNull(item, "item é obrigatório");
		Pedido atual = pedidos.buscarPorId(pedidoId)
				.orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));
		return pedidos.salvar(atual.adicionarItem(item));
	}

}
