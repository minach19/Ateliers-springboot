# Stratégie de fetch et de cascade

Les associations utilisent `FetchType.LAZY` afin de ne charger les entités liées
qu'en cas de besoin. Les opérations qui ont besoin d'une relation doivent donc
lire celle-ci dans une transaction ou utiliser une requête adaptée.

| Association | Fetch | Cascade | Justification |
| --- | --- | --- | --- |
| Contrat – Paiement | LAZY | ALL et `orphanRemoval = true` | Un paiement dépend de son contrat : sa création, sa mise à jour et sa suppression suivent le cycle de vie du contrat. Retirer un paiement de la collection le supprime également. |
| Agence – Véhicule | LAZY | Aucune | Un véhicule peut subsister à la suppression de son agence. |
| Agence – Employé | LAZY | Aucune | Un employé peut subsister à la suppression de son agence. |
| Véhicule – Équipement | LAZY | Aucune | Les équipements sont partagés ; leur cycle de vie n'est pas celui d'un véhicule. La collection est un `Set` pour éviter les doublons. |
| Client – Réservation | LAZY | PERSIST | Enregistrer un client peut enregistrer ses nouvelles réservations, sans propager les mises à jour ou suppressions. |
| Réservation – Véhicule | LAZY | Aucune | La réservation référence un véhicule existant, dont le cycle de vie est indépendant. |
| Réservation – Contrat | LAZY | ALL côté Réservation | Le contrat est géré avec sa réservation et ne doit pas lui survivre. La réservation est le côté cascade de cette association. |
| Véhicule – Maintenance | LAZY | PERSIST | Enregistrer un véhicule peut enregistrer ses nouvelles maintenances ; modifier ou supprimer un véhicule ne supprime pas l'historique par cascade. |
