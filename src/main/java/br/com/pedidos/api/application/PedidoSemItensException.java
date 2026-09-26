package br.com.pedidos.api.application;

public class PedidoSemItensException extends IllegalArgumentException {

	public PedidoSemItensException(String mensagem) {
		super(mensagem);
	}

}
