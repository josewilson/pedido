package br.com.pedidos.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedidoItensTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final ItemPedido PAO = new ItemPedido("PAO-100", 1, new BigDecimal("5.50"));

	@Test
	@DisplayName("A1: adicionar item devolve outro objeto")
	void adicionarDevolveOutroObjeto() {
		Pedido original = Pedido.novo("c-1");

		Pedido resultado = original.adicionarItem(CAFE);

		assertThat(resultado).isNotSameAs(original);
	}

	@Test
	@DisplayName("A2: adicionar item não altera o pedido original")
	void adicionarNaoAlteraOOriginal() {
		Pedido original = Pedido.novo("c-1");

		original.adicionarItem(CAFE);

		assertThat(original.itens()).isEmpty();
		assertThat(original.total()).isEqualByComparingTo("0.00");
	}

	@Test
	@DisplayName("A3: o resultado é o original com o item, mantendo id, cliente e status")
	void resultadoEOOriginalMaisOItem() {
		Pedido original = Pedido.novo("c-1");

		Pedido resultado = original.adicionarItem(CAFE);

		assertThat(resultado.id()).isEqualTo(original.id());
		assertThat(resultado.clienteId()).isEqualTo("c-1");
		assertThat(resultado.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(resultado.itens()).containsExactly(CAFE);
	}

	@Test
	@DisplayName("A4: itens entram no final, em ordem")
	void itensEntramEmOrdem() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE).adicionarItem(PAO);

		assertThat(pedido.itens()).containsExactly(CAFE, PAO);
	}

	@Test
	@DisplayName("A5: o mesmo produto adicionado duas vezes gera duas linhas, sem somar quantidades")
	void mesmoProdutoGeraDuasLinhas() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE).adicionarItem(CAFE);

		assertThat(pedido.itens()).hasSize(2);
		assertThat(pedido.itens()).allSatisfy(item -> assertThat(item.quantidade()).isEqualTo(2));
		assertThat(pedido.total()).isEqualByComparingTo("75.60");
	}

	@Test
	@DisplayName("A6: item nulo é recusado")
	void recusaItemNulo() {
		Pedido pedido = Pedido.novo("c-1");

		assertThatNullPointerException().isThrownBy(() -> pedido.adicionarItem(null));
	}

	@Test
	@DisplayName("A7: pedido pago recusa novos itens")
	void pagoRecusaItem() {
		Pedido pago = Pedido.novo("c-1").adicionarItem(CAFE).pagar();

		assertThatIllegalStateException().isThrownBy(() -> pago.adicionarItem(PAO));
	}

	@Test
	@DisplayName("A8: pedido cancelado recusa novos itens")
	void canceladoRecusaItem() {
		Pedido cancelado = Pedido.novo("c-1").adicionarItem(CAFE).cancelar();

		assertThatIllegalStateException().isThrownBy(() -> cancelado.adicionarItem(PAO));
	}

	@Test
	@DisplayName("A9: item recusado não altera o pedido")
	void recusaNaoAlteraOPedido() {
		Pedido pago = Pedido.novo("c-1").adicionarItem(CAFE).pagar();

		assertThatIllegalStateException().isThrownBy(() -> pago.adicionarItem(PAO));

		assertThat(pago.itens()).containsExactly(CAFE);
		assertThat(pago.status()).isEqualTo(StatusPedido.PAGO);
	}

	@Test
	@DisplayName("C1: alterar a lista usada para construir o pedido não altera o pedido")
	void copiaAListaRecebida() {
		List<ItemPedido> lista = new ArrayList<>(List.of(CAFE));
		Pedido pedido = new Pedido(UUID.randomUUID(), "c-1", StatusPedido.ABERTO, lista);

		lista.add(PAO);
		lista.clear();

		assertThat(pedido.itens()).containsExactly(CAFE);
	}

	@Test
	@DisplayName("C2: a lista exposta não aceita adição")
	void listaExpostaNaoAceitaAdicao() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> pedido.itens().add(PAO));
	}

	@Test
	@DisplayName("C3: a lista exposta não aceita remoção nem limpeza")
	void listaExpostaNaoAceitaRemocao() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> pedido.itens().remove(0));
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> pedido.itens().clear());
		assertThat(pedido.itens()).containsExactly(CAFE);
	}

	@Test
	@DisplayName("C4: lista com elemento nulo é recusada")
	void recusaListaComElementoNulo() {
		List<ItemPedido> comNulo = Arrays.asList(CAFE, null);

		assertThatNullPointerException()
				.isThrownBy(() -> new Pedido(UUID.randomUUID(), "c-1", StatusPedido.ABERTO, comNulo));
	}

	@Test
	@DisplayName("C5: a lista de um pedido vazio também não é modificável")
	void listaDePedidoVazioNaoEModificavel() {
		Pedido pedido = Pedido.novo("c-1");

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> pedido.itens().add(CAFE));
	}

}
