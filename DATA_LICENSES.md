# Knowledge Corpus Licensing and Attribution

The Apache License 2.0 in `LICENSE` covers the FitPlan RAG source code. It does
not relicense source material represented in `Data/knowledge-base`.

The public corpus contains 49 Chinese machine translations of government text.
Each Markdown file records its publisher, canonical URL, retrieval date,
source language, translation method, content hash, and source-specific license.
The translations are unofficial and are not endorsed by any source agency.

## Included sources

| Files | Source | Terms and required attribution |
| ---: | --- | --- |
| 45 | U.S. Department of Health and Human Services, Office of Disease Prevention and Health Promotion (ODPHP) | ODPHP states that information on its site is in the public domain and may be distributed and copied. Reusers should link to the ODPHP website and acknowledge ODPHP as the source. See <https://odphp.health.gov/copyright-policy>. |
| 2 | National Institute of Diabetes and Digestive and Kidney Diseases (NIDDK), NIH | NIDDK states that the majority of its information is copyright-free and may be reproduced, subject to exceptions for sponsored documents, third-party material, graphics, and logos. Edited content must remove NIH/NIDDK logos and must not imply endorsement. See <https://www.niddk.nih.gov/copyright>. |
| 1 | National Institute of Arthritis and Musculoskeletal and Skin Diseases (NIAMS), NIH | NIAMS states that website text is in the public domain and may be reproduced. Third-party photographs and illustrations are excluded. See <https://www.niams.nih.gov/disclaimer>. |
| 1 | UK Department of Health and Social Care / Office for Health Improvement and Disparities | Licensed under the Open Government Licence v3.0. Required attribution: “Contains public sector information licensed under the Open Government Licence v3.0.” See <https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/>. |

To the extent that project contributors hold copyright in the machine-produced
Chinese translations, translation cleanup, or corpus metadata derived from the
U.S. public-domain sources, those contributions are offered under CC BY 4.0:
<https://creativecommons.org/licenses/by/4.0/>. The UK source and its adaptation
remain subject to the Open Government Licence v3.0.

Do not use agency names, seals, logos, or source material in a way that suggests
official status, sponsorship, or endorsement. Verify the canonical source
before relying on a translated passage for health decisions.

## Excluded sources

The public release intentionally excludes 40 locally retained translations:

- 37 HPRC/Uniformed Services University articles, because the referenced site
  notice does not contain an explicit grant covering translation and
  redistribution.
- 3 Dietary Guidelines documents, pending preservation and verification of
  their source-specific public-domain reuse record.

These files may exist in a contributor's ignored `Data/restricted-local/`
directory. They are not part of the repository, release, or default corpus and
must not be committed without documented permission and a maintainer review.
