package br.com.pedidos.api.application.port.out;

import java.util.Optional;
import java.util.UUID;

import br.com.pedidos.api.domain.Pedido;

public interface Pedidos {

	Pedido salvar(Pedido pedido);

	Optional<Pedido> buscarPorId(UUID id);

}
