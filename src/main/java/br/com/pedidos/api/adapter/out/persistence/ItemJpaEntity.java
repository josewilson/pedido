package br.com.pedidos.api.adapter.out.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_pedido")
class ItemJpaEntity {

	// Chave técnica: o item é um valor do domínio e não tem identidade própria.
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sku", nullable = false)
	private String sku;

	@Column(name = "quantidade", nullable = false)
	private int quantidade;

	@Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal precoUnitario;

	protected ItemJpaEntity() {
	}

	ItemJpaEntity(String sku, int quantidade, BigDecimal precoUnitario) {
		this.sku = sku;
		this.quantidade = quantidade;
		this.precoUnitario = precoUnitario;
	}

	String getSku() {
		return sku;
	}

	int getQuantidade() {
		return quantidade;
	}

	BigDecimal getPrecoUnitario() {
		return precoUnitario;
	}

}
