package br.com.pedidos.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemPedidoTest {

	private static final BigDecimal PRECO = new BigDecimal("18.90");

	@Test
	@DisplayName("I1: guarda produto, quantidade e preço como informados")
	void guardaOQueRecebe() {
		ItemPedido item = new ItemPedido("CAFE-500", 2, PRECO);

		assertThat(item.codigoProduto()).isEqualTo("CAFE-500");
		assertThat(item.quantidade()).isEqualTo(2);
		assertThat(item.precoUnitario()).isEqualByComparingTo("18.90");
	}

	@Test
	@DisplayName("I2: subtotal é quantidade vezes preço, com escala 2")
	void subtotalEQuantidadeVezesPreco() {
		BigDecimal subtotal = new ItemPedido("CAFE-500", 2, PRECO).subtotal();

		assertThat(subtotal).isEqualByComparingTo("37.80");
		assertThat(subtotal.scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("I3: subtotal é exato, sem erro de ponto flutuante")
	void subtotalEExato() {
		BigDecimal subtotal = new ItemPedido("BALA-10", 3, new BigDecimal("0.10")).subtotal();

		assertThat(subtotal).isEqualByComparingTo("0.30");
		assertThat(subtotal.toPlainString()).isEqualTo("0.30");
	}

	@ParameterizedTest(name = "I4/I5: quantidade {0} é recusada")
	@ValueSource(ints = { 0, -1, -100 })
	void recusaQuantidadeNaoPositiva(int quantidade) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ItemPedido("CAFE-500", quantidade, PRECO));
	}

	@ParameterizedTest(name = "I6/I7: preço {0} é recusado")
	@ValueSource(strings = { "0", "0.00", "-0.01", "-18.90" })
	void recusaPrecoNaoPositivo(String preco) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, new BigDecimal(preco)));
	}

	@ParameterizedTest(name = "I8: preço {0} tem mais de 2 casas e é recusado")
	@ValueSource(strings = { "18.905", "0.001", "18.901" })
	void recusaPrecoComMaisDeDuasCasas(String preco) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, new BigDecimal(preco)));
	}

	@Test
	@DisplayName("I9: preços 18.9, 18.90 e 18.900 viram o mesmo valor, com escala 2")
	void normalizaEscalaDoPreco() {
		ItemPedido umaCasa = new ItemPedido("CAFE-500", 2, new BigDecimal("18.9"));
		ItemPedido duasCasas = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
		ItemPedido tresCasas = new ItemPedido("CAFE-500", 2, new BigDecimal("18.900"));

		assertThat(umaCasa).isEqualTo(duasCasas).isEqualTo(tresCasas);
		assertThat(umaCasa.precoUnitario().scale()).isEqualTo(2);
		assertThat(tresCasas.precoUnitario().scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("I10: preço nulo é recusado")
	void recusaPrecoNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, null));
	}

	@Test
	@DisplayName("I11: código do produto nulo é recusado")
	void recusaCodigoNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> new ItemPedido(null, 2, PRECO));
	}

	@ParameterizedTest(name = "I12: código \"{0}\" em branco é recusado")
	@ValueSource(strings = { "", "   " })
	void recusaCodigoEmBranco(String codigo) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new ItemPedido(codigo, 2, PRECO));
	}

	@Test
	@DisplayName("X1: recusas de item lançam ItemInvalidoException, que é uma IllegalArgumentException")
	void recusasDeItemSaoItemInvalidoException() {
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("CAFE-500", 0, PRECO));
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("CAFE-500", -1, PRECO));
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, new BigDecimal("0.00")));
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, new BigDecimal("-0.01")));
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("CAFE-500", 2, new BigDecimal("18.905")));
		assertThatExceptionOfType(ItemInvalidoException.class)
				.isThrownBy(() -> new ItemPedido("   ", 2, PRECO));
		assertThat(new ItemInvalidoException("x")).isInstanceOf(IllegalArgumentException.class);
	}

}
