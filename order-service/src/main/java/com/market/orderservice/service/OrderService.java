package com.market.orderservice.service;


import com.market.orderservice.dto.InventoryResponse;
import com.market.orderservice.dto.OrderRequest;
import com.market.orderservice.dto.OredrLineItemsDto;
import com.market.orderservice.model.Order;
import com.market.orderservice.model.OrderLineItems;
import com.market.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    private final WebClient.Builder webClientBuilder;

    private final Tracer tracer;

    public String placeOrder(OrderRequest orderRequest) {

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        var orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto).collect(Collectors.toList());
        order.setOrderLineItems(orderLineItems);

        List<String> skuCodes = order.getOrderLineItems()
                .stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());

        Span inventeryServiceLookup =tracer.nextSpan().name("inventeryServiceLookup");

        try(Tracer.SpanInScope spanInScope=tracer.withSpan(inventeryServiceLookup.start())){
            //check products are exists
            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode",skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();
            boolean allProductsInStock = Arrays.stream(inventoryResponseArray).allMatch(InventoryResponse::isInStock);
            if (Boolean.TRUE.equals(allProductsInStock)) {
                orderRepository.save(order);
                return "Order placed successfully";
            } else {
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }
        }finally {
            inventeryServiceLookup.end();
        }


    }

    private OrderLineItems mapToDto(OredrLineItemsDto oredrLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setQuantity(oredrLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(oredrLineItemsDto.getSkuCode());
        orderLineItems.setPrice(oredrLineItemsDto.getPrice());
        return orderLineItems;
    }
}
