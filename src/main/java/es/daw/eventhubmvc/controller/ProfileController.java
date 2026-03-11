package es.daw.eventhubmvc.controller;

import es.daw.eventhubmvc.dto.UserProfileUpdateRequest;
import es.daw.eventhubmvc.entity.User;
import es.daw.eventhubmvc.exception.EmailAlreadyExistsException;
import es.daw.eventhubmvc.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Locale;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final MessageSource messageSource;

    @GetMapping("/edit-principal")
    public String editProfilePrincipal(
            Principal principal,
            Model model
    ){

        // Principal solo me da el nombre del usuario autenticado
        // Podría utiliar el repo de usuario y a través de principal.getName() obtener
        // la entidad User... noooooooooooooooooo
        // no usarmos un repo directamente en un controlador, usamos un servicio con la
        // lógica de negocio!!!!
        User user =  userService.findByUsername(principal.getName());

        // 1. crear el objeto dto con los campos del entity user
        UserProfileUpdateRequest form = new UserProfileUpdateRequest(
                user.getFullName(),
                user.getEmail(),
                null,
                null
        );



        // 2. pasar al model el dto como setAttribute

        model.addAttribute("form", form);

        // 3. return de la vista th
        return "profile/edit";




    }

    // En vez de usar Profile, usar @AuthenticationPrincipal
    // Anotación de Spring y me ahorro usar el repo para obtener el Usuario
    @GetMapping("/edit")
    public String editProfile(
            @AuthenticationPrincipal User user,
            Model model
    ){
        UserProfileUpdateRequest form = new UserProfileUpdateRequest(
                user.getFullName(),
                user.getEmail(),
                null,
                null
        );

        model.addAttribute("form", form);
        return "profile/edit";

    }

    @PostMapping("/edit")
    public String updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute("form") UserProfileUpdateRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Locale locale
    ){
        // --------------------------------
        // Validar que las pwd coincidan. Solo si vienen rellenas
        if (form.password() != null && !form.password().isBlank()) {
            if (form.confirmPassword() == null || !form.confirmPassword().equals(form.password())) {
                // añadir un error al campo
                // errorCode -> message.properties --> i18n (internacionalización)
                bindingResult.rejectValue("confirmPassword", "password.mismatch");
                //bindingResult.rejectValue("confirmPassword", "password.mismatch","Las contraseñas no coinciden");
                //return "profile/edit";
            }
        }

        // Si errores de validación renderizar de nuevo la vida
        if (bindingResult.hasErrors()) {
            return "profile/edit";
        }
        // --------------------------------

        // Obtener el usuario para actualizar los campos
        try {
            userService.updateProfile(user.getId(), form);
        }catch (EmailAlreadyExistsException e){
            bindingResult.rejectValue("email","email.duplicate");
            //bindingResult.rejectValue("email","email.duplicate.form.email");
            return "profile/edit";
        }

        // Si pasa por aquí todo ha ido OK

        // Sin internacionalización!!!!
        //redirectAttributes.addFlashAttribute("success", "Perfil actualizado correctamente");

        // Obtener el mensaje internacionalizado usando la clave 'profile.updated'
        String successMessage = messageSource.getMessage("profile.updated", null, locale);
        redirectAttributes.addFlashAttribute("success", successMessage);

        // Usar redirect attributes para mostrar el mensaje de perfil actualizado ok
//        GET  /profile/edit  → mostrar formulario
//        POST /profile/edit  → procesar datos
//        REDIRECT /profile/edit
//        GET  /profile/edit  → mostrar resultado
        return "redirect:/profile/edit";
    }

}
