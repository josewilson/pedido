package br.com.pedidos.api.application;

import java.util.List;
import java.util.Objects;

import br.com.pedidos.api.application.port.in.CriarPedido;
import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

public class CriarPedidoService implements CriarPedido {

	private final Pedidos pedidos;

	public CriarPedidoService(Pedidos pedidos) {
		this.pedidos = Objects.requireNonNull(pedidos, "pedidos é obrigatório");
	}

	@Override
	public Pedido criar(String clienteId, List<ItemPedido> itens) {
		Objects.requireNonNull(itens, "itens é obrigatório");
		if (itens.isEmpty()) {
			throw new PedidoSemItensException("o pedido precisa de pelo menos um item");
		}
		Pedido pedido = Pedido.novo(clienteId);
		for (ItemPedido item : itens) {
			pedido = pedido.adicionarItem(item);
		}
		return pedidos.salvar(pedido);
	}

}
