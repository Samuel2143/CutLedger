package com.cutledger.payment;

import com.cutledger.workspace.SessionService.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments){this.payments=payments;}
    public record Acceptance(@NotBlank @Size(min=64,max=64) String acceptedSnapshotHash){}
    public record Capture(@NotBlank @Size(max=100) String returnedOrderId){}
    @PostMapping("/quotes/{id}/payments") @ResponseStatus(HttpStatus.ACCEPTED)
    Object initialize(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("If-Match") String match,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Acceptance input){return payments.initialize(session,id,input.acceptedSnapshotHash,match,key);}
    @GetMapping("/payments/{id}") Object payment(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){return payments.view(session,id);}
    @PostMapping("/payments/{id}/capture") @ResponseStatus(HttpStatus.ACCEPTED)
    Object capture(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody Capture input){return payments.capture(session,id,input.returnedOrderId,key);}
    @PostMapping("/payments/{id}/cancel") Object cancel(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("Idempotency-Key") UUID key){return payments.cancel(session,id,key);}
    @PostMapping("/payments/{id}/reconcile") @ResponseStatus(HttpStatus.ACCEPTED)
    Object reconcile(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("Idempotency-Key") UUID key){return payments.reconcile(session,id,key);}
    @GetMapping("/payments/{id}/receipt") Object receipt(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){return payments.receipt(session,id);}
}
