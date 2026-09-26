package br.com.pedidos.api.application;

import java.util.UUID;

public class PedidoNaoEncontradoException extends RuntimeException {

	public PedidoNaoEncontradoException(UUID pedidoId) {
		super("pedido não encontrado: " + pedidoId);
	}

}
