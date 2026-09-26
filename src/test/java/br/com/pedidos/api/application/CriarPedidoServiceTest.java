package br.com.pedidos.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.pedidos.api.PedidosEmMemoria;
import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;
import br.com.pedidos.api.domain.StatusPedido;

class CriarPedidoServiceTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final ItemPedido PAO = new ItemPedido("PAO-100", 1, new BigDecimal("5.50"));

	private final PedidosEmMemoria memoria = new PedidosEmMemoria();
	private final CriarPedidoService servico = new CriarPedidoService(memoria);

	@Test
	@DisplayName("S1: cria o pedido aberto para o cliente, com os itens informados")
	void criaOPedidoParaOCliente() {
		Pedido criado = servico.criar("c-1", List.of(CAFE));

		assertThat(criado.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(criado.clienteId()).isEqualTo("c-1");
		assertThat(criado.itens()).containsExactly(CAFE);
		assertThat(criado.total()).isEqualByComparingTo("37.80");
	}

	@Test
	@DisplayName("S2: guarda exatamente um pedido, com o mesmo id do devolvido")
	void guardaOPedido() {
		Pedido criado = servico.criar("c-1", List.of(CAFE));

		assertThat(memoria.salvos()).extracting(Pedido::id).containsExactly(criado.id());
	}

	@Test
	@DisplayName("S3: devolve o pedido que a porta de saída devolveu")
	void devolveOQueAPortaDevolveu() {
		Pedido devolvidoPelaPorta = Pedido.novo("outro-cliente");
		Pedidos pedidos = new PedidosEmMemoria() {
			@Override
			public Pedido salvar(Pedido recebido) {
				return devolvidoPelaPorta;
			}
		};

		Pedido resultado = new CriarPedidoService(pedidos).criar("c-1", List.of(CAFE));

		assertThat(resultado).isSameAs(devolvidoPelaPorta);
	}

	@Test
	@DisplayName("S4: mantém os itens na ordem informada e soma o total")
	void mantemAOrdemDosItens() {
		Pedido criado = servico.criar("c-1", List.of(CAFE, PAO));

		assertThat(criado.itens()).containsExactly(CAFE, PAO);
		assertThat(criado.total()).isEqualByComparingTo("43.30");
	}

	@Test
	@DisplayName("S5: o mesmo produto duas vezes gera duas linhas, sem somar quantidades")
	void mesmoProdutoGeraDuasLinhas() {
		Pedido criado = servico.criar("c-1", List.of(CAFE, CAFE));

		assertThat(criado.itens()).hasSize(2);
		assertThat(criado.itens()).allSatisfy(item -> assertThat(item.quantidade()).isEqualTo(2));
	}

	@Test
	@DisplayName("S6 e S7: lista vazia é recusada e nada é guardado")
	void recusaListaVazia() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> servico.criar("c-1", List.of()));

		assertThat(memoria.salvos()).isEmpty();
	}

	@Test
	@DisplayName("S8: lista nula é recusada e nada é guardado")
	void recusaListaNula() {
		assertThatNullPointerException()
				.isThrownBy(() -> servico.criar("c-1", null));

		assertThat(memoria.salvos()).isEmpty();
	}

	@Test
	@DisplayName("S9: item nulo é recusado e nada é guardado")
	void recusaItemNulo() {
		List<ItemPedido> comNulo = Arrays.asList(CAFE, null);

		assertThatNullPointerException()
				.isThrownBy(() -> servico.criar("c-1", comNulo));

		assertThat(memoria.salvos()).isEmpty();
	}

	@Test
	@DisplayName("S10: cliente nulo é recusado e nada é guardado")
	void recusaClienteNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> servico.criar(null, List.of(CAFE)));

		assertThat(memoria.salvos()).isEmpty();
	}

	@Test
	@DisplayName("S11: cliente em branco é recusado e nada é guardado")
	void recusaClienteEmBranco() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> servico.criar("  ", List.of(CAFE)));

		assertThat(memoria.salvos()).isEmpty();
	}

	@Test
	@DisplayName("S12: falha ao guardar sobe, sem ser escondida nem trocada")
	void falhaAoGuardarSobe() {
		RuntimeException falha = new RuntimeException("falha ao guardar");
		Pedidos pedidos = new PedidosEmMemoria() {
			@Override
			public Pedido salvar(Pedido recebido) {
				throw falha;
			}
		};

		assertThatThrownBy(() -> new CriarPedidoService(pedidos).criar("c-1", List.of(CAFE)))
				.isSameAs(falha);
	}

	@Test
	@DisplayName("S13: a porta de saída é obrigatória")
	void recusaPedidosNulo() {
		assertThatNullPointerException()
				.isThrownBy(() -> new CriarPedidoService(null));
	}

	@Test
	@DisplayName("S14: c-1 compra CAFE-500, 2 x 18.90, e o pedido guardado tem total 37.80")
	void exemploDaAula() {
		Pedido criado = servico.criar("c-1", List.of(new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"))));

		assertThat(criado.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(criado.total()).isEqualByComparingTo("37.80");
		assertThat(memoria.salvos()).hasSize(1);
		assertThat(memoria.salvos().get(0).total()).isEqualByComparingTo("37.80");
	}

	@Test
	@DisplayName("X2: lista vazia lança PedidoSemItensException, que é uma IllegalArgumentException, e nada é guardado")
	void listaVaziaLancaPedidoSemItensException() {
		assertThatExceptionOfType(PedidoSemItensException.class)
				.isThrownBy(() -> servico.criar("c-1", List.of()));
		assertThat(new PedidoSemItensException("x")).isInstanceOf(IllegalArgumentException.class);
		assertThat(memoria.salvos()).isEmpty();
	}

}
