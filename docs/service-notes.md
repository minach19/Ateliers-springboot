# Notes — IoC, injection et couche Service

Les packages du projet utilisent le préfixe `tn.esprit.autoloc.autolocapi`.
Le package racine de `@SpringBootApplication` est donc l'ancêtre des packages
`domain`, `repository`, `service` et `exception` : le scan Spring les détecte.

## A. Modes d'injection comparés

| Critère | Injection par constructeur | Injection par attribut |
| --- | --- | --- |
| Champ `final` possible | Oui | Non |
| Dépendance visible dans la signature | Oui, dans le constructeur | Non, elle est cachée dans le champ annoté |
| Utilisable hors conteneur | Oui, à condition de fournir la dépendance au constructeur | Non sans injection préalable ; l'attribut reste `null` |
| Signalement SonarQube for IDE | Pas le défaut signalé par la règle S6813 | Généralement signalée par S6813, qui recommande l'injection par constructeur |

Tous les services de ce projet utilisent un champ `final`, un constructeur
généré par Lombok (`@RequiredArgsConstructor`) et une dépendance de type
interface. Spring injecte le repository dans l'unique constructeur.

## B. Services et dépendances

| Service | Dépendance injectée | Mode | Justification |
| --- | --- | --- | --- |
| `AgenceServiceImpl` | `IAgenceRepository` | Constructeur | L'interface permet de remplacer le repository et d'isoler le service. |
| `ClientServiceImpl` | `IClientRepository` | Constructeur | Même convention ; le service applique les règles d'identification du client. |
| `ContratServiceImpl` | `IContratRepository` | Constructeur | Le service protège le montant et laisse la suppression des paiements à la cascade JPA. |
| `EmployeServiceImpl` | `IEmployeRepository` | Constructeur | Le service vérifie les attributs obligatoires avant persistance. |
| `EquipementServiceImpl` | `IEquipementRepository` | Constructeur | Le service vérifie le libellé ; il ne possède pas de champ quantité dans le modèle. |
| `MaintenanceServiceImpl` | `IMaintenanceRepository` | Constructeur | Le service contrôle la cohérence des dates avant persistance. |
| `PaiementServiceImpl` | `IPaiementRepository` | Constructeur | Lecture seule : les paiements sont créés, modifiés et supprimés via leur contrat. |
| `ReservationServiceImpl` | `IReservationRepository` | Constructeur | Le service contrôle l'ordre des dates de réservation. |
| `VehiculeServiceImpl` | `IVehiculeRepository` | Constructeur | Le service refuse un tarif journalier nul ou négatif. |

Les services sont sans état : aucun ne conserve de données propres à un appel
dans un champ modifiable. Les mises à jour chargent l'entité persistée et
recopient ses champs modifiables, sans remplacer l'identifiant ni les collections
et associations. Les relations restent gérées séparément, conformément au
périmètre de cet atelier.

## C. Diagnostic des messages du conteneur

| Message | Cause probable | Correction |
| --- | --- | --- |
| `IContratService` introuvable | `ContratServiceImpl` n'est pas déclarée comme composant (`@Service` absent), ou est hors du périmètre de scan Spring. | Ajouter `@Service` et placer l'implémentation sous le package racine de `@SpringBootApplication`. Vérifier également que la classe implémente bien `IContratService`. |
| Deux beans pour le même type (`emailNotificateur`, `smsNotificateur`) | Plusieurs implémentations correspondent à un point d'injection unique. | Placer `@Primary` sur l'implémentation par défaut, ou `@Qualifier("emailNotificateur")` sur le paramètre du constructeur qui doit recevoir cette implémentation. |
| Cycle entre `clientServiceImpl` et `reservationServiceImpl` | Chaque service exige l'autre dans son constructeur ; aucun ne peut être construit en premier. | Supprimer la dépendance circulaire : déplacer la logique commune dans un troisième service, ou faire orchestrer les deux opérations par une couche supérieure. Éviter de masquer le cycle en passant à l'injection par attribut. |

## D. Anomalies et corrections de conception

| Règle ou problème | Risque | Correction |
| --- | --- | --- |
| S6813 — injection par attribut | Dépendance cachée, champ non `final`, objet incomplet lorsqu'il est construit hors du conteneur. | Les services utilisent l'injection par constructeur avec `@RequiredArgsConstructor` et un champ `final`. |
| S112 — exception générique (expérience demandée dans l'atelier) | Une `RuntimeException` générique ne décrit pas le cas métier et complique le traitement de la ressource absente. | L'expérience temporaire est écartée : `findById` et `deleteById` lèvent `ResourceNotFoundException`. |
| Mise à jour par `save(entiteReçue)` (risque de conception lié au modèle) | Les champs omis peuvent écraser ceux de la base ; remplacer la collection des paiements peut déclencher `orphanRemoval`. | Charger l'entité existante, valider les nouvelles données, recopier les champs modifiables, puis sauvegarder l'entité chargée. |

Les deux identifiants S6813 et S112 sont ceux indiqués par le support de
l'atelier. Le troisième point est une prévention de perte de données liée à
`update`, et non une règle SonarQube déclarée comme exécutée. SonarQube for IDE
n'a pas été lancé pendant cette réalisation.

## E. Questions de compréhension

1. **Où est le `new` de `ContratServiceImpl` ? Qui l'exécute ?**  
   Il n'y a pas de `new ContratServiceImpl(...)` dans le code de l'application.
   Au démarrage, le conteneur Spring détecte `@Service`, résout
   `IContratRepository`, puis instancie le service en appelant son constructeur.

2. **Pourquoi un contrôleur dépend-il de `IContratService` plutôt que de
   `ContratServiceImpl` ?**  
   Il dépend du contrat fonctionnel, pas d'une réalisation particulière. Cela
   réduit le couplage et permet de remplacer ou simuler l'implémentation sans
   modifier le contrôleur.

3. **Pourquoi un service singleton doit-il rester sans état ?**  
   La même instance sert plusieurs requêtes et threads. Stocker le « contrat
   courant » dans un champ modifiable permettrait à une requête d'écraser la
   valeur utilisée par une autre.

4. **Pourquoi `update` charge-t-il l'entité avant `save` ?**  
   Pour ne modifier que les champs explicitement pris en charge, conserver les
   autres valeurs persistées et éviter d'effacer par accident des relations ou
   une collection avec `orphanRemoval`.

5. **Quelle différence entre `@Component` et `@Bean` ? Entre `@Primary` et
   `@Qualifier` ? Pourquoi `IPaiementService` est-il en lecture seule ?**  
   `@Component` (et ses spécialisations comme `@Service`) déclare une classe
   détectée par scan ; `@Bean` déclare explicitement, dans une classe
   `@Configuration`, l'objet retourné par une méthode. `@Primary` choisit le
   candidat par défaut parmi plusieurs beans ; `@Qualifier` sélectionne un bean
   précis au point d'injection. Le paiement dépend de son contrat et le modèle
   propage déjà les opérations du contrat aux paiements : son service n'expose
   donc que la lecture.
