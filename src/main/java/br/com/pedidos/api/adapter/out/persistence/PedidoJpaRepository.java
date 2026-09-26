package br.com.pedidos.api.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, UUID> {

}
