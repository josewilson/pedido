package br.com.pedidos.api.application.port.in;

import java.util.UUID;

import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;

public interface AdicionarItem {

	Pedido adicionar(UUID pedidoId, ItemPedido item);

}
