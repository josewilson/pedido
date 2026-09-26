package br.com.pedidos.api.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.pedidos.api.application.port.out.Pedidos;
import br.com.pedidos.api.domain.ItemPedido;
import br.com.pedidos.api.domain.Pedido;
import br.com.pedidos.api.domain.StatusPedido;

/**
 * Teste de integração contra o Postgres local (infra/docker-compose.yml).
 * Não roda no `mvnw test` padrão: execute com `.\mvnw.cmd test "-Dtest=*IT"`.
 * Sem transação ao redor do teste, cada chamada ao adapter usa a sua própria transação e sessão.
 * Os dados gravados não são apagados, para a tabela poder ser consultada depois.
 */
@SpringBootTest
class PedidosJpaAdapterIT {

	private static final ItemPedido CAFE = new ItemPedido("CAFE-500", 2, new BigDecimal("18.90"));
	private static final ItemPedido PAO = new ItemPedido("PAO-100", 1, new BigDecimal("5.50"));

	@Autowired
	private PedidosJpaAdapter adapter;

	@Autowired
	private Pedidos porta;

	@Autowired
	private JdbcTemplate jdbc;

	// J9: guarda contra gravar em outro banco por engano, antes de qualquer escrita.
	@BeforeEach
	void garanteQueEstaNoBancoPedidos() {
		assertThat(jdbc.queryForObject("select current_database()", String.class))
				.as("os testes de integração só podem gravar no banco 'pedidos'")
				.isEqualTo("pedidos");
	}

	@Test
	@DisplayName("J1: salva o pedido de c-1 e o recupera por UUID em outra transação, com itens, status e total")
	void salvaERecuperaEmOutraTransacao() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		adapter.salvar(pedido);
		Optional<Pedido> recuperado = adapter.buscarPorId(pedido.id());

		assertThat(recuperado).isPresent();
		assertThat(recuperado.get().clienteId()).isEqualTo("c-1");
		assertThat(recuperado.get().status()).isEqualTo(StatusPedido.ABERTO);
		assertThat(recuperado.get().itens()).containsExactly(new ItemPedido("CAFE-500", 2, new BigDecimal("18.90")));
		assertThat(recuperado.get().total()).isEqualByComparingTo("37.80");
		assertThat(recuperado.get().total().scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("J2: o id é o do domínio, sem o adapter gerar outro")
	void preservaOIdDoDominio() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		Pedido salvo = adapter.salvar(pedido);
		Pedido recuperado = adapter.buscarPorId(pedido.id()).orElseThrow();

		assertThat(salvo.id()).isEqualTo(pedido.id());
		assertThat(recuperado.id()).isEqualTo(pedido.id());
		assertThat(jdbc.queryForObject("select count(*) from pedido where id = ?", Integer.class, pedido.id()))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("J3: salvar devolve o pedido salvo")
	void salvarDevolveOPedidoSalvo() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		Pedido salvo = adapter.salvar(pedido);

		assertThat(salvo).isEqualTo(pedido);
		assertThat(salvo.total()).isEqualByComparingTo("37.80");
	}

	@Test
	@DisplayName("J4: mantém a ordem dos itens e as linhas repetidas")
	void mantemOrdemELinhasRepetidas() {
		Pedido pedido = Pedido.novo("c-2").adicionarItem(CAFE).adicionarItem(PAO).adicionarItem(CAFE);

		adapter.salvar(pedido);
		Pedido recuperado = adapter.buscarPorId(pedido.id()).orElseThrow();

		assertThat(recuperado.itens()).containsExactly(CAFE, PAO, CAFE);
	}

	@Test
	@DisplayName("J5: UUID desconhecido devolve vazio")
	void uuidDesconhecidoDevolveVazio() {
		assertThat(adapter.buscarPorId(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("J6: salvar de novo atualiza o mesmo registro, sem duplicar pedido nem itens")
	void salvarDeNovoAtualiza() {
		Pedido aberto = Pedido.novo("c-3").adicionarItem(CAFE).adicionarItem(PAO);

		adapter.salvar(aberto);
		adapter.salvar(aberto.pagar());
		Pedido recuperado = adapter.buscarPorId(aberto.id()).orElseThrow();

		assertThat(recuperado.status()).isEqualTo(StatusPedido.PAGO);
		assertThat(recuperado.itens()).containsExactly(CAFE, PAO);
		assertThat(jdbc.queryForObject("select count(*) from pedido where id = ?", Integer.class, aberto.id()))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from item_pedido where pedido_id = ?", Integer.class, aberto.id()))
				.isEqualTo(2);
	}

	@Test
	@DisplayName("J8: a tabela pedido não tem coluna total, e os itens guardam sku, quantidade e preço")
	void esquemaDasTabelas() {
		List<String> colunasDePedido = colunasDe("pedido");
		List<String> colunasDeItem = colunasDe("item_pedido");

		assertThat(colunasDePedido).containsExactlyInAnyOrder("id", "cliente_id", "status");
		assertThat(colunasDePedido).doesNotContain("total");
		assertThat(colunasDeItem).contains("sku", "quantidade", "preco_unitario");
	}

	@Test
	@DisplayName("A1: buscarPorId faz parte da porta Pedidos: recupera o pedido salvo e devolve vazio para UUID desconhecido")
	void buscarPorIdPelaPorta() {
		Pedido pedido = Pedido.novo("c-1").adicionarItem(CAFE);

		porta.salvar(pedido);

		assertThat(porta.buscarPorId(pedido.id())).contains(pedido);
		assertThat(porta.buscarPorId(UUID.randomUUID())).isEmpty();
	}

	@Test
	@DisplayName("A2: buscar, adicionar item e salvar de novo mantém a mesma linha, com 2 itens em ordem e total 56.70")
	void adicionarItemESalvarDeNovo() {
		Pedido inicial = Pedido.novo("c-1").adicionarItem(CAFE);
		ItemPedido maisUmCafe = new ItemPedido("CAFE-500", 1, new BigDecimal("18.90"));

		adapter.salvar(inicial);
		Pedido carregado = adapter.buscarPorId(inicial.id()).orElseThrow();
		adapter.salvar(carregado.adicionarItem(maisUmCafe));
		Pedido recuperado = adapter.buscarPorId(inicial.id()).orElseThrow();

		assertThat(recuperado.id()).isEqualTo(inicial.id());
		assertThat(recuperado.itens()).containsExactly(CAFE, maisUmCafe);
		assertThat(recuperado.total()).isEqualByComparingTo("56.70");
		assertThat(jdbc.queryForObject("select count(*) from pedido where id = ?", Integer.class, inicial.id()))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from item_pedido where pedido_id = ?", Integer.class,
				inicial.id())).isEqualTo(2);
	}

	private List<String> colunasDe(String tabela) {
		return jdbc.queryForList(
				"select column_name from information_schema.columns where table_schema = 'public' and table_name = ?",
				String.class, tabela);
	}

}
