package br.com.pedidos.api.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.Pedido;

@Component
public class PedidosJpaAdapter implements Pedidos {

	private final PedidoJpaRepository repositorio;

	PedidosJpaAdapter(PedidoJpaRepository repositorio) {
		this.repositorio = repositorio;
	}

	// O mapeamento acontece dentro da transação, antes de a sessão fechar.
	@Override
	@Transactional
	public Pedido salvar(Pedido pedido) {
		PedidoJpaEntity salvo = repositorio.save(PedidoMapper.paraEntidade(pedido));
		return PedidoMapper.paraDominio(salvo);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Pedido> buscarPorId(UUID id) {
		return repositorio.findById(id).map(PedidoMapper::paraDominio);
	}

}
