package br.com.pedidos.api.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record ItemPedido(String codigoProduto, int quantidade, BigDecimal precoUnitario) {

	static final int ESCALA = 2;

	public ItemPedido {
		Objects.requireNonNull(codigoProduto, "codigoProduto é obrigatório");
		Objects.requireNonNull(precoUnitario, "precoUnitario é obrigatório");
		if (codigoProduto.isBlank()) {
			throw new ItemInvalidoException("codigoProduto não pode estar em branco");
		}
		if (quantidade <= 0) {
			throw new ItemInvalidoException("quantidade deve ser maior que zero");
		}
		if (precoUnitario.signum() <= 0) {
			throw new ItemInvalidoException("precoUnitario deve ser maior que zero");
		}
		if (precoUnitario.stripTrailingZeros().scale() > ESCALA) {
			throw new ItemInvalidoException("precoUnitario aceita no máximo " + ESCALA + " casas decimais");
		}
		// Nunca arredonda: preços com mais de ESCALA casas significativas já foram recusados acima.
		precoUnitario = precoUnitario.setScale(ESCALA, RoundingMode.UNNECESSARY);
	}

	public BigDecimal subtotal() {
		return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
	}

}
