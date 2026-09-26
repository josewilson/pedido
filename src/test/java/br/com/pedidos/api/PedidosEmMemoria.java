package br.com.pedidos.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.Pedido;

/**
 * Dublê de teste da porta Pedidos, sem Spring nem banco.
 * `guardar` prepara o cenário e não conta como salvamento. `salvar` e `buscarPorId` são contados.
 * Pode ser estendido em um teste para simular uma falha ou um retorno diferente.
 */
public class PedidosEmMemoria implements Pedidos {

	private final Map<UUID, Pedido> pedidos = new LinkedHashMap<>();
	private final List<UUID> buscas = new ArrayList<>();
	private int salvamentos;

	public void guardar(Pedido pedido) {
		pedidos.put(pedido.id(), pedido);
	}

	@Override
	public Pedido salvar(Pedido pedido) {
		salvamentos++;
		pedidos.put(pedido.id(), pedido);
		return pedido;
	}

	@Override
	public Optional<Pedido> buscarPorId(UUID id) {
		buscas.add(id);
		return Optional.ofNullable(pedidos.get(id));
	}

	public List<Pedido> salvos() {
		return List.copyOf(pedidos.values());
	}

	public List<UUID> buscas() {
		return List.copyOf(buscas);
	}

	public int salvamentos() {
		return salvamentos;
	}

}
