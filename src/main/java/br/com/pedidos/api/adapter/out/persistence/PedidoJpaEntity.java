package br.com.pedidos.api.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.pedidos.api.domain.StatusPedido;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "pedido")
class PedidoJpaEntity {

	// O id vem do domínio. Sem @GeneratedValue: o banco nunca gera outro.
	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "cliente_id", nullable = false)
	private String clienteId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private StatusPedido status;

	// Não existe coluna de total: o total é derivado dos itens no domínio.
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "pedido_id", nullable = false)
	@OrderColumn(name = "posicao")
	private List<ItemJpaEntity> itens = new ArrayList<>();

	protected PedidoJpaEntity() {
	}

	PedidoJpaEntity(UUID id, String clienteId, StatusPedido status, List<ItemJpaEntity> itens) {
		this.id = id;
		this.clienteId = clienteId;
		this.status = status;
		this.itens = new ArrayList<>(itens);
	}

	UUID getId() {
		return id;
	}

	String getClienteId() {
		return clienteId;
	}

	StatusPedido getStatus() {
		return status;
	}

	List<ItemJpaEntity> getItens() {
		return itens;
	}

}
