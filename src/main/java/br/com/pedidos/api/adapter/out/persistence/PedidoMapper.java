package br.com.pedidos.api.adapter.out.persistence;

import java.util.List;

import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

final class PedidoMapper {

	private PedidoMapper() {
	}

	static PedidoJpaEntity paraEntidade(Pedido pedido) {
		List<ItemJpaEntity> itens = pedido.itens().stream()
				.map(item -> new ItemJpaEntity(item.codigoProduto(), item.quantidade(), item.precoUnitario()))
				.toList();
		return new PedidoJpaEntity(pedido.id(), pedido.clienteId(), pedido.status(), itens);
	}

	static Pedido paraDominio(PedidoJpaEntity entidade) {
		List<ItemPedido> itens = entidade.getItens().stream()
				.map(item -> new ItemPedido(item.getSku(), item.getQuantidade(), item.getPrecoUnitario()))
				.toList();
		return new Pedido(entidade.getId(), entidade.getClienteId(), entidade.getStatus(), itens);
	}

}
