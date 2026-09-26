package br.com.pedidos.api.application.port.in;

import java.util.List;

import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

public interface CriarPedido {

	Pedido criar(String clienteId, List<ItemPedido> itens);

}
