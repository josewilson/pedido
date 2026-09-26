package br.com.pedidos.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PedidoTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final ItemPedido PAO = new ItemPedido("PAO-100", 1, new BigDecimal("5.50"));

	@Test
	@DisplayName("P1: pedido novo é um rascunho aberto e vazio, com id")
	void pedidoNovoEUmRascunho() {
		Pedido pedido = Pedido.novo("c-1");

		assertThat(pedido.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(pedido.itens()).isEmpty();
		assertThat(pedido.clienteId()).isEqualTo("c-1");
		assertThat(pedido.id()).isNotNull();
	}

	@Test
	@DisplayName("P2: dois pedidos novos têm ids diferentes")
	void idsSaoUnicos() {
		assertThat(Pedido.novo("c-1").id()).isNotEqualTo(Pedido.novo("c-1").id());
	}

	@Test
	@DisplayName("P3: total de pedido vazio é 0.00, com escala 2")
	void totalDoPedidoVazio() {
		BigDecimal total = Pedido.novo("c-1").total();

		assertThat(total).isEqualByComparingTo("0.00");
		assertThat(total.scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("P4: total é derivado dos itens, com escala 2")
	void totalDerivado() {
		BigDecimal total = Pedido.novo("c-1").adicionarItem(CAFE).total();

		assertThat(total).isEqualByComparingTo("37.80");
		assertThat(total.scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("P5: total soma os subtotais de todos os itens")
	void totalSomaOsSubtotais() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE).adicionarItem(PAO);

		assertThat(pedido.total()).isEqualByComparingTo("43.30");
	}

	@Test
	@DisplayName("P6: cliente nulo é recusado")
	void recusaClienteNulo() {
		assertThatNullPointerException().isThrownBy(() -> Pedido.novo(null));
	}

	@ParameterizedTest(name = "P7: cliente \"{0}\" em branco é recusado")
	@ValueSource(strings = { "", "  " })
	void recusaClienteEmBranco(String cliente) {
		assertThatIllegalArgumentException().isThrownBy(() -> Pedido.novo(cliente));
	}

	@Test
	@DisplayName("P8: construtor recusa id nulo")
	void construtorRecusaIdNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> new Pedido(null, "c-1", StatusPedido.ABERTO, List.of()));
	}

	@Test
	@DisplayName("P8: construtor recusa status nulo")
	void construtorRecusaStatusNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> new Pedido(UUID.randomUUID(), "c-1", null, List.of()));
	}

	@Test
	@DisplayName("P8: construtor recusa lista de itens nula")
	void construtorRecusaItensNulos() {
		assertThatNullPointerException()
				.isThrownBy(() -> new Pedido(UUID.randomUUID(), "c-1", StatusPedido.ABERTO, null));
	}

	@Test
	@DisplayName("E1: c-1 compra CAFE-500, 2 x 18.90, e o total é 37.80")
	void exemploDaAula() {
		Pedido pedido = Pedido.novo("c-1")
				.adicionarItem(new ItemPedido("CAFE-500", 2, new BigDecimal("18.90")));

		assertThat(pedido.clienteId()).isEqualTo("c-1");
		assertThat(pedido.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(pedido.itens()).hasSize(1);
		assertThat(pedido.total()).isEqualByComparingTo("37.80");
	}

	@Test
	@DisplayName("E2: depois de pago, o pedido do exemplo mantém o total e recusa novos itens")
	void exemploDaAulaPagoRecusaItem() {
		Pedido pago = Pedido.novo("c-1").adicionarItem(CAFE).pagar();

		assertThat(pago.status()).isEqualTo(StatusPedido.PAGO);
		assertThat(pago.total()).isEqualByComparingTo("37.80");
		assertThatIllegalStateException().isThrownBy(() -> pago.adicionarItem(PAO));
	}

}
