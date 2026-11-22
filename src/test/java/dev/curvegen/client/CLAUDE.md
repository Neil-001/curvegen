# Client test contracts

- Every production text literal must be a translation key or reviewed technical syntax. `TranslationSourceTest` skips quoted text in comments and character literals. It accepts prefixes for generated keys and `LangTest` checks the generated names. `TranslationResourcesTest` uses the locales shared with Chroma Studio, including the English source.
- Build placement feedback fixtures like Minecraft's command replies, including empty wrappers, syntax context with a suggested command, and coordinates supplied as text. The client recognizes replies by component structure.
- Keep the legacy pattern fixture without pin or save time. The reader supplies defaults for both.
