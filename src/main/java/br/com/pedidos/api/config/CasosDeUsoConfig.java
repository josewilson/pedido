package br.com.pedidos.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.pedidos.api.application.AdicionarItemService;
import br.com.pedidos.api.application.CriarPedidoService;
import br.com.pedidos.api.application.port.in.AdicionarItem;
import br.com.pedidos.api.application.port.in.CriarPedido;
import br.com.pedidos.api.application.port.out.Pedidos;

@Configuration
public class CasosDeUsoConfig {

	@Bean
	public CriarPedido criarPedido(Pedidos pedidos) {
		return new CriarPedidoService(pedidos);
	}

	@Bean
	public AdicionarItem adicionarItem(Pedidos pedidos) {
		return new AdicionarItemService(pedidos);
	}

}
