package br.com.pedidos.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedidoTransicoesTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));

	private static Pedido aberto() {
		return Pedido.novo("c-1").adicionarItem(CAFE);
	}

	@Test
	@DisplayName("T1: pedido aberto pode ser pago, mantendo id, cliente e itens")
	void abertoPodeSerPago() {
		Pedido aberto = aberto();

		Pedido pago = aberto.pagar();

		assertThat(pago.status()).isEqualTo(StatusPedido.PAGO);
		assertThat(pago.id()).isEqualTo(aberto.id());
		assertThat(pago.clienteId()).isEqualTo(aberto.clienteId());
		assertThat(pago.itens()).isEqualTo(aberto.itens());
	}

	@Test
	@DisplayName("T2: pedido aberto pode ser cancelado, mantendo id, cliente e itens")
	void abertoPodeSerCancelado() {
		Pedido aberto = aberto();

		Pedido cancelado = aberto.cancelar();

		assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
		assertThat(cancelado.id()).isEqualTo(aberto.id());
		assertThat(cancelado.clienteId()).isEqualTo(aberto.clienteId());
		assertThat(cancelado.itens()).isEqualTo(aberto.itens());
	}

	@Test
	@DisplayName("T3: pagar devolve outro objeto e o original continua aberto")
	void pagarDevolveOutroObjeto() {
		Pedido aberto = aberto();

		Pedido pago = aberto.pagar();

		assertThat(pago).isNotSameAs(aberto);
		assertThat(aberto.status()).isEqualTo(StatusPedido.ABERTO);
	}

	@Test
	@DisplayName("T4: cancelar devolve outro objeto e o original continua aberto")
	void cancelarDevolveOutroObjeto() {
		Pedido aberto = aberto();

		Pedido cancelado = aberto.cancelar();

		assertThat(cancelado).isNotSameAs(aberto);
		assertThat(aberto.status()).isEqualTo(StatusPedido.ABERTO);
	}

	@Test
	@DisplayName("T5: pedido pago não pode ser pago de novo")
	void pagoNaoPagaDeNovo() {
		Pedido pago = aberto().pagar();

		assertThatIllegalStateException().isThrownBy(pago::pagar);
	}

	@Test
	@DisplayName("T6: pedido pago não pode ser cancelado")
	void pagoNaoCancela() {
		Pedido pago = aberto().pagar();

		assertThatIllegalStateException().isThrownBy(pago::cancelar);
	}

	@Test
	@DisplayName("T7: pedido cancelado não pode ser pago")
	void canceladoNaoPaga() {
		Pedido cancelado = aberto().cancelar();

		assertThatIllegalStateException().isThrownBy(cancelado::pagar);
	}

	@Test
	@DisplayName("T8: pedido cancelado não pode ser cancelado de novo")
	void canceladoNaoCancelaDeNovo() {
		Pedido cancelado = aberto().cancelar();

		assertThatIllegalStateException().isThrownBy(cancelado::cancelar);
	}

	@Test
	@DisplayName("T9: transição recusada não altera status nem itens")
	void recusaNaoAlteraOPedido() {
		Pedido pago = aberto().pagar();
		Pedido cancelado = aberto().cancelar();

		assertThatIllegalStateException().isThrownBy(pago::pagar);
		assertThatIllegalStateException().isThrownBy(pago::cancelar);
		assertThatIllegalStateException().isThrownBy(cancelado::pagar);
		assertThatIllegalStateException().isThrownBy(cancelado::cancelar);

		assertThat(pago.status()).isEqualTo(StatusPedido.PAGO);
		assertThat(pago.itens()).containsExactly(CAFE);
		assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
		assertThat(cancelado.itens()).containsExactly(CAFE);
	}

	@Test
	@DisplayName("D1: pedido pago recusa item com PedidoFechadoException, que é uma IllegalStateException")
	void pagoRecusaItemComPedidoFechadoException() {
		Pedido pago = aberto().pagar();

		assertThatExceptionOfType(PedidoFechadoException.class)
				.isThrownBy(() -> pago.adicionarItem(CAFE))
				.withMessageContaining("adicionar item")
				.withMessageContaining("PAGO");
		assertThat(new PedidoFechadoException("x")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("D2: pedido cancelado recusa item com PedidoFechadoException")
	void canceladoRecusaItemComPedidoFechadoException() {
		Pedido cancelado = aberto().cancelar();

		assertThatExceptionOfType(PedidoFechadoException.class)
				.isThrownBy(() -> cancelado.adicionarItem(CAFE))
				.withMessageContaining("adicionar item")
				.withMessageContaining("CANCELADO");
	}

	@Test
	@DisplayName("D3: pagar e cancelar em pedido pago ou cancelado lançam PedidoFechadoException")
	void transicoesRecusadasSaoPedidoFechadoException() {
		Pedido pago = aberto().pagar();
		Pedido cancelado = aberto().cancelar();

		assertThatExceptionOfType(PedidoFechadoException.class).isThrownBy(pago::pagar);
		assertThatExceptionOfType(PedidoFechadoException.class).isThrownBy(pago::cancelar);
		assertThatExceptionOfType(PedidoFechadoException.class).isThrownBy(cancelado::pagar);
		assertThatExceptionOfType(PedidoFechadoException.class).isThrownBy(cancelado::cancelar);
	}

}
