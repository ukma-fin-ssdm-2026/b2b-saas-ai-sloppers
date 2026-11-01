package ua.edu.ukma.community.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.edu.ukma.community.auth.CurrentUser;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.common.ApiPaths;
import ua.edu.ukma.community.config.OpenApiConfig;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.V1 + "/organizations/{organizationId}/subscription")
@Tag(name = "Subscriptions")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class SubscriptionController {

    private static final String ORG_ID_EXAMPLE = "3f6c1f0e-8a2b-4c1d-9e7a-1b2c3d4e5f60";

    private final SubscriptionService subscriptions;
    private final CurrentUser currentUser;

    public SubscriptionController(SubscriptionService subscriptions, CurrentUser currentUser) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(operationId = "getOrganizationSubscription",
            summary = "Чи підписаний поточний користувач на організацію")
    @ApiResponse(responseCode = "200", description = "Стан підписки")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public SubscriptionStatusResponse get(
            @Parameter(description = "Ідентифікатор організації", example = ORG_ID_EXAMPLE)
            @PathVariable UUID organizationId) {
        User user = currentUser.require();
        return new SubscriptionStatusResponse(subscriptions.isSubscribed(organizationId, user.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "subscribeToOrganization",
            summary = "Підписатися на нові події організації",
            description = "Листи надходять на email акаунту, коли організація публікує нову подію.")
    @ApiResponse(responseCode = "204", description = "Підписку створено або вона вже існувала")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void subscribe(
            @Parameter(description = "Ідентифікатор організації", example = ORG_ID_EXAMPLE)
            @PathVariable UUID organizationId) {
        User user = currentUser.require();
        subscriptions.subscribe(organizationId, user.id());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unsubscribeFromOrganization", summary = "Відписатися від організації")
    @ApiResponse(responseCode = "204", description = "Підписку знято (або її не було)")
    @ApiResponse(responseCode = "401", ref = OpenApiConfig.UNAUTHORIZED)
    @ApiResponse(responseCode = "404", ref = OpenApiConfig.NOT_FOUND)
    public void unsubscribe(
            @Parameter(description = "Ідентифікатор організації", example = ORG_ID_EXAMPLE)
            @PathVariable UUID organizationId) {
        User user = currentUser.require();
        subscriptions.unsubscribe(organizationId, user.id());
    }
}
