# ItemCatalog

Catalogo item universale per server Minecraft (Spigot/Paper), autore **MasterCraft**.

Legge gli item da **MMOItems**, **Oraxen**, **ItemsAdder** ed **ExecutableItems** e li
espone in un'unica GUI centralizzata, con categorie/sottocategorie
personalizzabili, ricerca, filtri e permessi granulari.

## Versioni supportate

Compilato contro `spigot-api 1.20.6-R0.1-SNAPSHOT`, usa solo API stabili
presenti già da Spigot **1.16.5**, quindi funziona su tutte le versioni
comprese fra **1.16.5 e 1.20.6**. Non usa NMS né API specifiche di una
singola versione.

## Come compilare

```bash
mvn clean package
```

Il jar finale viene generato in `target/ItemCatalog-1.0.0.jar`.

> **Nota sull'ambiente in cui è stato scritto questo plugin:** il sandbox
> usato per generare questo progetto ha accesso di rete limitato (solo
> npm/pypi/github/crates) e **non può raggiungere i repository Maven di
> Spigot/Paper**, quindi il codice non è stato compilato/testato qui.
> È stato scritto e revisionato con la massima attenzione, ma esegui
> `mvn clean package` sulla tua macchina (con accesso a internet) come
> primo passo prima di metterlo in produzione, e segnalami qualsiasi
> errore di compilazione: lo risolvo subito.

## Architettura Provider/Adapter

Il core (`ItemCatalogPlugin`, `ItemRegistry`, `CategoryResolver`, GUI,
comandi) **non dipende in alcun modo** dalle API di MMOItems / Oraxen /
ItemsAdder / ExecutableItems, nemmeno a compile-time: ogni provider in
`provider/impl/` parla con il proprio plugin **esclusivamente via
reflection** (`util/ReflectionUtil.java`). Questo significa:

- il progetto compila con la sola dipendenza `spigot-api`;
- un plugin mancante o con una versione API leggermente diversa non può
  mai far crashare il core, nemmeno a livello di `ClassNotFoundException`;
- `ProviderManager` attiva un provider **solo** se il plugin corrispondente
  risulta installato **e** abilitato in `config.yml`.

I nomi dei metodi reflected (documentati nei commenti di ogni
`XxxProvider.java`) rispecchiano le API pubbliche più comuni delle
rispettive versioni recenti (MMOItems 6.9.x, Oraxen `OraxenItems`,
ItemsAdder `CustomStack`, ExecutableItems `ExecutableItemsAPI`). Se la
versione installata sul tuo server ha rinominato un metodo, va corretto
solo nella relativa classe provider — nessun altro file va toccato.

## Sistema di categorie

- `categories.yml` — albero categorie/sottocategorie, ordine, nome,
  icona, e mapping "categoria nativa del provider -> categoria interna".
- `items.yml` — override manuale per singolo item (priorità assoluta).
- `config.yml` (`auto-classify.rules`) — classificazione automatica per
  Material/keyword quando non c'è né override né categoria nativa.

Priorità di risoluzione, in ordine: **override manuale → categoria nativa
mappata → auto-classify → `miscellaneous`**.

## Comandi

| Comando | Alias | Descrizione |
|---|---|---|
| `/catalog` | `/items`, `/catalogo` | Apre la GUI del catalogo |
| `/catalog search <nome>` | | Cerca un item per nome (case-insensitive) |
| `/itemcatalog reload` | | Ricarica config + rilegge tutti i provider |

## Permessi

- `itemcatalog.use` (default: true) — apre la GUI
- `itemcatalog.search` (default: true) — usa la ricerca
- `itemcatalog.admin` (default: op) — bypassa `hide-not-obtainable-from-players`
- `itemcatalog.reload` (default: op)
- `itemcatalog.category.<id>` / `itemcatalog.category.<id>.<subid>` — generati
  automaticamente all'avvio per ogni categoria/sottocategoria definita in
  `categories.yml`. Effettivi solo se `permissions.require-permission-per-category: true`
  in `config.yml`.

## File di configurazione

- `config.yml` — debug, GUI, ricerca, filtri, provider on/off + esclusioni,
  regole di auto-classify.
- `categories.yml` — albero categorie + mapping categorie native.
- `items.yml` — override manuali per singolo item.

## Note performance

`ItemRegistry` è progettato per migliaia di item: ogni lookup (per id,
categoria, provider, ricerca) passa da indici pre-costruiti in `HashMap`,
mai da una scansione lineare di tutta la lista tranne durante il rebuild
(fatto una sola volta per reload, non per ogni richiesta).
