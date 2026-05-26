# Recargas Rapidas Integration Design

## Goal

Integrate Recargas Rapidas into LotteryNet's native recargas screen so cashiers can sell mobile recargas and paqueticos from the app while keeping a local, auditable record.

## Scope

The first implementation adds a provider bridge and UI contracts for:

- Claro, Altice, Viva, Digicel, and Natcom as local-logo providers.
- Recarga sales through `POST /refill/add`.
- Paquetico lookup through `POST /refill/paquetico/info`.
- Paquetico purchase through `POST /refill/paquetico/add`.
- Secure credential entry through local configuration, not hard-coded credentials.

It does not perform a live sale during development without explicit user approval at the final confirmation step.

## Architecture

Add a focused `core/recharge/recargasrapidas` integration layer that maps LotteryNet providers to Recargas Rapidas provider values. The UI continues to use `RecargasActivity`, with a compact mode switch between recargas and paqueticos, and the existing local repository remains the audit source for visible history.

The API bridge stores remote status and references in extended recharge records. A transaction starts as pending, calls the provider, and is marked approved or failed based on the provider response.

## Data Flow

1. User selects provider and enters phone.
2. In recarga mode, user enters or picks a valid amount.
3. In paquetico mode, app queries available paqueticos before purchase.
4. App confirms the operation before calling the provider purchase endpoint.
5. Successful provider response is saved locally with provider reference.
6. Failed provider response is saved as failed without pretending the sale completed.

## Provider Mapping

- Claro: API value `Claro`, minimum RD$25.
- Altice: API value `Orange`, minimum RD$30.
- Viva: API value `Viva`, minimum RD$20.
- Digicel: API value `DigiCel`.
- Natcom: API value `Natcom`, minimum RD$50.

Local logos are required from `app/src/main/assets`: `logo_claro.svg`, `logo_altice.svg`, `logo_viva.svg`, `logo_digicel.svg`, and `logo_natcom.svg`.

## Safety

No credentials are hard-coded. The bridge reads credentials from local secure configuration. Live sale calls are isolated behind explicit submit actions and response parsing records provider errors.

## Testing

Use unit contracts for provider mapping, amount validation, endpoint paths, payload sanitization, response parsing, and UI mode contracts. Build verification must run `assembleDebug`.
