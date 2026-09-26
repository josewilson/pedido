package br.com.pedidos.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.pedidos.api.PedidosEmMemoria;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;
import br.com.pedidos.api.domain.PedidoFechadoException;
import br.com.pedidos.api.domain.StatusPedido;

class AdicionarItemServiceTest {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final ItemPedido MAIS_UM_CAFE = new ItemPedido("CAFE-500", 1, new BigDecimal("18.90"));
	private static final ItemPedido PAO = new ItemPedido("PAO-100", 1, new BigDecimal("5.50"));

	private final PedidosEmMemoria memoria = new PedidosEmMemoria();
	private final AdicionarItemService servico = new AdicionarItemService(memoria);
	private final Pedido existente = Pedido.novo("c-1").adicionarItem(CAFE);

	@BeforeEach
	void guardaOPedidoExistente() {
		memoria.guardar(existente);
	}

	@Test
	@DisplayName("U1: adiciona CAFE-500 1 x 18.90 ao pedido de 37.80: mesmo id, dois itens, total 56.70")
	void exemploDaAula() {
		Pedido atualizado = servico.adicionar(existente.id(), MAIS_UM_CAFE);

		assertThat(atualizado.id()).isEqualTo(existente.id());
		assertThat(atualizado.clienteId()).isEqualTo("c-1");
		assertThat(atualizado.status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(atualizado.itens()).containsExactly(CAFE, MAIS_UM_CAFE);
		assertThat(atualizado.total()).isEqualByComparingTo("56.70");
		assertThat(atualizado.total().scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("U2: busca o pedido pela porta, uma vez, com o id informado")
	void buscaPelaPorta() {
		servico.adicionar(existente.id(), MAIS_UM_CAFE);

		assertThat(memoria.buscas()).containsExactly(existente.id());
	}

	@Test
	@DisplayName("U3: salva o pedido atualizado pela porta, uma vez, sem duplicar")
	void salvaPelaPorta() {
		servico.adicionar(existente.id(), MAIS_UM_CAFE);

		assertThat(memoria.salvamentos()).isEqualTo(1);
		assertThat(memoria.salvos()).hasSize(1);
		assertThat(memoria.salvos().get(0).id()).isEqualTo(existente.id());
		assertThat(memoria.salvos().get(0).itens()).hasSize(2);
	}

	@Test
	@DisplayName("U4: devolve o pedido que a porta de saída devolveu")
	void devolveOQueAPortaDevolveu() {
		Pedido devolvidoPelaPorta = Pedido.novo("outro-cliente");
		PedidosEmMemoria porta = new PedidosEmMemoria() {
			@Override
			public Pedido salvar(Pedido recebido) {
				return devolvidoPelaPorta;
			}
		};
		porta.guardar(existente);

		Pedido resultado = new AdicionarItemService(porta).adicionar(existente.id(), MAIS_UM_CAFE);

		assertThat(resultado).isSameAs(devolvidoPelaPorta);
	}

	@Test
	@DisplayName("U5: o item entra no final da lista")
	void itemEntraNoFinal() {
		Pedido comDoisItens = Pedido.novo("c-2").adicionarItem(CAFE).adicionarItem(PAO);
		memoria.guardar(comDoisItens);

		Pedido atualizado = servico.adicionar(comDoisItens.id(), MAIS_UM_CAFE);

		assertThat(atualizado.itens()).containsExactly(CAFE, PAO, MAIS_UM_CAFE);
	}

	@Test
	@DisplayName("U6: pedido inexistente lança PedidoNaoEncontradoException com o id, e nada é salvo")
	void pedidoInexistente() {
		UUID desconhecido = UUID.randomUUID();

		assertThatExceptionOfType(PedidoNaoEncontradoException.class)
				.isThrownBy(() -> servico.adicionar(desconhecido, MAIS_UM_CAFE))
				.withMessageContaining(desconhecido.toString());
		assertThat(memoria.salvamentos()).isZero();
	}

	@Test
	@DisplayName("U7: pedido pago lança PedidoFechadoException, nada é salvo e a memória fica igual")
	void pedidoPago() {
		Pedido pago = existente.pagar();
		memoria.guardar(pago);

		assertThatExceptionOfType(PedidoFechadoException.class)
				.isThrownBy(() -> servico.adicionar(pago.id(), MAIS_UM_CAFE));
		assertThat(memoria.salvamentos()).isZero();
		assertThat(memoria.salvos()).containsExactly(pago);
	}

	@Test
	@DisplayName("U8: pedido cancelado lança PedidoFechadoException, nada é salvo e a memória fica igual")
	void pedidoCancelado() {
		Pedido cancelado = existente.cancelar();
		memoria.guardar(cancelado);

		assertThatExceptionOfType(PedidoFechadoException.class)
				.isThrownBy(() -> servico.adicionar(cancelado.id(), MAIS_UM_CAFE));
		assertThat(memoria.salvamentos()).isZero();
		assertThat(memoria.salvos()).containsExactly(cancelado);
	}

	@Test
	@DisplayName("U9: item nulo é recusado e nada é salvo")
	void recusaItemNulo() {
		assertThatNullPointerException().isThrownBy(() -> servico.adicionar(existente.id(), null));

		assertThat(memoria.salvamentos()).isZero();
	}

	@Test
	@DisplayName("U10: id nulo é recusado")
	void recusaIdNulo() {
		assertThatNullPointerException().isThrownBy(() -> servico.adicionar(null, MAIS_UM_CAFE));

		assertThat(memoria.salvamentos()).isZero();
	}

	@Test
	@DisplayName("U11: a porta de saída é obrigatória")
	void recusaPedidosNulo() {
		assertThatNullPointerException().isThrownBy(() -> new AdicionarItemService(null));
	}

	@Test
	@DisplayName("U12: falha ao buscar sobe intacta, e nada é salvo")
	void falhaAoBuscarSobe() {
		RuntimeException falha = new RuntimeException("falha ao buscar");
		PedidosEmMemoria porta = new PedidosEmMemoria() {
			@Override
			public Optional<Pedido> buscarPorId(UUID id) {
				throw falha;
			}
		};

		assertThatThrownBy(() -> new AdicionarItemService(porta).adicionar(existente.id(), MAIS_UM_CAFE))
				.isSameAs(falha);
		assertThat(porta.salvamentos()).isZero();
	}

	@Test
	@DisplayName("U13: falha ao salvar sobe intacta")
	void falhaAoSalvarSobe() {
		RuntimeException falha = new RuntimeException("falha ao salvar");
		PedidosEmMemoria porta = new PedidosEmMemoria() {
			@Override
			public Pedido salvar(Pedido recebido) {
				throw falha;
			}
		};
		porta.guardar(existente);

		assertThatThrownBy(() -> new AdicionarItemService(porta).adicionar(existente.id(), MAIS_UM_CAFE))
				.isSameAs(falha);
	}

	@Test
	@DisplayName("U14: o pedido buscado não é alterado")
	void naoAlteraOQueBuscou() {
		servico.adicionar(existente.id(), MAIS_UM_CAFE);

		assertThat(existente.itens()).containsExactly(CAFE);
		assertThat(existente.total()).isEqualByComparingTo("37.80");
	}

}
