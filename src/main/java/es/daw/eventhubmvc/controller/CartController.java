package es.daw.eventhubmvc.controller;

import es.daw.eventhubmvc.dto.cart.AddToCartForm;
import es.daw.eventhubmvc.entity.Purchase;
import es.daw.eventhubmvc.enums.TicketCategory;
import es.daw.eventhubmvc.model.Cart;
import es.daw.eventhubmvc.model.CartItem;
import es.daw.eventhubmvc.service.CatalogClientService;
import es.daw.eventhubmvc.service.PurchaseService;
import es.daw.eventhubmvc.dto.ticket.TicketTypeDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class CartController {

    private final Cart cart;
    private final CatalogClientService catalogClientService;
    private final PurchaseService purchaseService;
    private final MessageSource messageSource;

    @GetMapping("/cart")
    public String view(Model model) {
        model.addAttribute("items", cart.getItems());
        model.addAttribute("total", cart.getTotal());
        return "cart/view";
    }

    @PostMapping("/cart/add")
    public String add(
            @Valid @ModelAttribute("addToCart") AddToCartForm form,
            RedirectAttributes ra,
            Locale locale
    ) {
        // 1. En form tenemos los valores de los parámetros del post
        // 2. Ejecuta Bean Validation (@NotBlank,@Min(1)....
        // MEJORA!!! no estamos recibiendo BindingResult, Spring lanzará error ...
        // no estamos controlando la validación...

        // VALIDACIÓN DEFENSIVA: evita que alguien envíe un ticketTypeCode inventado
        // No nos fiamos del ticketTypeCode que viene del cliente.
//        <input type="hidden" name="eventCode" th:value="${event.code}"/>
//        <input type="hidden" name="ticketTypeCode" th:value="${t.code}"/>
        // Vamos al catálogo (api data rest) y para recuperar los TicketTypeDTO del evento.
        List<TicketTypeDTO> ticketTypes =
                catalogClientService.findTicketTypesByEventCode(form.eventCode())
                        .content();

//        TicketTypeDTO ticketType = ticketTypes.stream()
//                .filter(t -> form.ticketTypeCode().equals(t.code()))
//                .findFirst()
//                .orElse(null);

        // De forma imperativa... neceisto un TicketTypeDTO
        TicketTypeDTO ticketType = null;
        for (TicketTypeDTO t : ticketTypes) {
            if (form.ticketTypeCode().equals(t.code())){
                ticketType = t;
                break;
            }
        }

        // Validaciones defensivas
        // MEJORA!!!! i18n
        // El mensaje del addFlashAttribute debe estar acorde el locale... repasar Locale y MessageSource
        if (ticketType == null) {
            ra.addFlashAttribute("errorMessage", "Ticket Type Not Found");
            return "redirect:/events/"+form.eventCode();
        }

        // Validación defensiva...
        BigDecimal unitPrice = ticketType.basePrice() != null
                ? ticketType.basePrice()
                : BigDecimal.ZERO;

        // ---------------------------------------------
        int max = ticketType.category().getMaxPerPurchase(); // si es VIP da 4, si es STUDENT da 2
        // cuańtos tickets hay en el carrito con
        //BUG
        //int currentQty = cart.getQty(ticketType.code()); // cantidad que existe previamente en el carrito
        int currentQty = cart.getQtyByCategory(ticketType.category());
        int qty = form.qty(); // cantidad que quiero comprar
        int resultingQty = qty + currentQty;

        if (resultingQty > max) {
            // no permitimos. Ha sobrepaso el máximo de tickets permitidos de ese tipo
            String msg = messageSource.getMessage("cart.error.maxTickets",
                    new Object[]{max, ticketType.category().getLabel(),currentQty},
                    locale);
            ra.addFlashAttribute("errorMessage",msg);
            return "redirect:/events/"+form.eventCode();
        }

        // -----------------------
        // Cuantos tickets de la categoría VIP hay en el carrito...
        int vipAlreadyInCart = cart.getQtyByCategory(TicketCategory.VIP); // nuevo método en Cart

        // Regla de descuento. Solo se aplica a tipo VIP
        // Tipo VIP tiene una máximo de 4 (puedo comprar en total como máximo 4
        // Si el ticket que añades es VIP y ya tienes casi el máximo permitido (max-1 o más), a los “nuevos VIP” les aplicas 10%.
        //
        boolean applyDiscount =
                ticketType.category() == TicketCategory.VIP
                && vipAlreadyInCart >= (ticketType.category().getMaxPerPurchase() - 1);

        CartItem item;

        if (applyDiscount) {
            // aplico un 10% de descuento. Opciones:
            // 1. precio orginal - 10% = precio final
            // 2. precio original * 0,90 = 90% del precio.
            BigDecimal discountedPrice =
                    unitPrice.multiply(BigDecimal.valueOf(0.9));

            item = new CartItem(
                    ticketType.code() + "_DISCOUNTED",
                    ticketType.category().name(),
                    discountedPrice,
                    form.qty()
            );

            // pendiente i18n
            ra.addFlashAttribute("successMessage","10% discount applied to he new VIP tickets");

        }else{
            // ------------------------------
            item = new CartItem(
                    ticketType.code(),
                    ticketType.category().name(),
                    unitPrice,
                    form.qty()
            );

            // ---------------------
            // -- i18n
            ra.addFlashAttribute("successMessage", messageSource.getMessage("cart.success.added", new Object[]{form.qty()}, locale));
            // ---------------------

        }

        cart.addOrIncrement(item);

        return "redirect:/events/" + form.eventCode();



    }

    @PostMapping("/cart/update")
    public String update(
            @RequestParam String ticketTypeCode,
            @RequestParam int qty
    ) {
        cart.updateQty(ticketTypeCode, qty);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam String ticketTypeCode) {
        cart.remove(ticketTypeCode);
        return "redirect:/cart";
    }

    @PostMapping("/cart/checkout")
    public String checkout(Authentication authentication) {

        Purchase purchase = purchaseService
                .createPurchaseFromCart(authentication.getName(), cart);

        cart.clear();

        return "redirect:/purchases/" + purchase.getId();
    }
}
