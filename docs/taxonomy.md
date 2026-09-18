# WasegMul Waste Taxonomy

Generated: 2026-09-17 21:57  
Source: `taxonomy.yaml` (single source of truth)

---

## Overview

```
TACO (53 raw classes)
  ├── Excluded: 3 ambiguous categories
  └── Mapped:   50 categories

YOLO (15 visual detector classes)
  └── Each class groups objects that LOOK similar

EfficientNet (30 semantic subclasses)
  └── Classifies what each YOLO crop actually IS
```

---

## YOLO Detection Classes (15 visual categories)

YOLO detects WHERE objects are based on visual appearance.

| ID | Class | Description |
|---:|-------|-------------|
| 0 | `battery` | Distinctive rectangular dark shape |
| 1 | `bottle` | Transparent PET bottle |
| 2 | `can` | Aluminum/steel cylindrical beverage container |
| 3 | `cardboard` | Brown corrugated sheet material |
| 4 | `cigarette` | Small white cylinder with orange filter tip |
| 5 | `cup` | Open-top drinking vessel |
| 6 | `electronic` | Circuit boards, chips, connectors |
| 7 | `food_waste` | Organic matter: peels, cores, leftovers |
| 8 | `glass_container` | Squat cylinder, wide mouth, no neck |
| 9 | `metal` | Small shiny metal foil packaging |
| 10 | `paper` | Glossy paper, visually paper-like |
| 11 | `plastic_bag` | Plastic shopping bag with handles |
| 12 | `plastic_container` | Clear plastic bubble on cardboard backing |
| 13 | `plastic_wrapper` | Metallized plastic film bag |
| 14 | `textile` | Garments |

---

## EfficientNet Classification Classes (30 final subclasses)

After YOLO crops a detection, EfficientNet classifies into these subclasses.

| ID | Class |
|---:|-------|
| 0 | `Air-Conditioner` |
| 1 | `Battery` |
| 2 | `Cardboard` |
| 3 | `Electronic Component` |
| 4 | `Electronic Device` |
| 5 | `Glass` |
| 6 | `Keyboard` |
| 7 | `Laptop` |
| 8 | `Metal` |
| 9 | `Microwave` |
| 10 | `Miscellaneous Trash` |
| 11 | `Mobile` |
| 12 | `Mouse` |
| 13 | `Organic` |
| 14 | `PCB` |
| 15 | `Paper` |
| 16 | `Plastic` |
| 17 | `Player` |
| 18 | `Printer` |
| 19 | `Refrigerator` |
| 20 | `Television` |
| 21 | `Textile Trash` |
| 22 | `Washing Machine` |
| 23 | `automobile wastes` |
| 24 | `clothing` |
| 25 | `disposable_plastic_cutlery` |
| 26 | `light bulbs` |
| 27 | `shoes` |
| 28 | `styrofoam_cups` |
| 29 | `styrofoam_food_containers` |

---

## TACO → YOLO Mapping (50 categories)

Every TACO category is mapped individually based on visual appearance.
Source: http://tacodataset.org/taxonomy

### `battery` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Battery | Distinctive rectangular dark shape |

### `bottle` (3 categories)

| TACO Category | Reason |
|---------------|--------|
| Clear plastic bottle | Transparent PET bottle |
| Glass bottle | Cylindrical with neck, same shape as plastic bottles |
| Other plastic bottle | HDPE/PET/PP bottles |

### `can` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Drink can | Aluminum/steel cylindrical beverage container |

### `cardboard` (4 categories)

| TACO Category | Reason |
|---------------|--------|
| Cardboard | Brown corrugated sheet material |
| Corrugated carton | Standard shipping box, visually identical to cardboard |
| Drink carton | Tetra Pak style rigid rectangular packaging |
| Meal carton | Takeaway packaging, same visual category |

### `cigarette` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Cigarette | Small white cylinder with orange filter tip |

### `cup` (3 categories)

| TACO Category | Reason |
|---------------|--------|
| Disposable plastic cup | Open-top drinking vessel |
| Other plastic cup | Generic plastic cups |
| Paper cup | Paper-based drinking cup |

### `electronic` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Electronic component | Circuit boards, chips, connectors |

### `food_waste` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Food waste | Organic matter: peels, cores, leftovers |

### `glass_container` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Glass jar | Squat cylinder, wide mouth, no neck |

### `metal` (5 categories)

| TACO Category | Reason |
|---------------|--------|
| Aluminium blister pack | Small shiny metal foil packaging |
| Metal bottle cap | Tiny metal disc (~2cm) |
| Pop tab | Tiny aluminum ring-pull |
| Scrap metal | Non-can metal fragments |
| Tin foil | Aluminum foil sheet |

### `paper` (5 categories)

| TACO Category | Reason |
|---------------|--------|
| Magazine | Glossy paper, visually paper-like |
| Newspaper | Newsprint, visually paper-like |
| Normal paper | Standard white office paper |
| Other paper | Generic paper items |
| Tissues | Crumpled white paper, visually paper-like |

### `plastic_bag` (1 categories)

| TACO Category | Reason |
|---------------|--------|
| Single-use carrier bag | Plastic shopping bag with handles |

### `plastic_container` (12 categories)

| TACO Category | Reason |
|---------------|--------|
| Carded blister pack | Clear plastic bubble on cardboard backing |
| Disposable food container | Black plastic trays, PET containers |
| Foam food container | Styrofoam takeaway box |
| Other plastic | Miscellaneous rigid plastic fragments |
| Other plastic container | Generic rigid plastic vessels |
| Plastic bottle cap | Small plastic disc |
| Plastic glooves | Rigid transparent plastic hand covering (original TACO typo) |
| Plastic gloves | Rigid transparent plastic hand covering |
| Plastic utensils | Forks, spoons, knives |
| Spread tub | Margarine/yoghurt tub |
| Squeezable tube | Toothpaste/glue tube |
| Styrofoam piece | Foam fragment, rigid shape |

### `plastic_wrapper` (7 categories)

| TACO Category | Reason |
|---------------|--------|
| Crisp packet | Metallized plastic film bag |
| Other plastic wrapper | Candy wrappers, retort pouches, yoghurt lids |
| Paper straw | Paper-based straw |
| Plastic film | Transparent/translucent plastic film sheets |
| Plastic straw | Thin plastic stick |
| Six pack rings | Thin plastic rings |
| Straw | Thin plastic stick |

### `textile` (4 categories)

| TACO Category | Reason |
|---------------|--------|
| Clothing | Garments |
| Rope & strings | Rope, fishing nets, string |
| Shoe | Footwear |
| Textile | Fabric items |

---

## Excluded Categories (3)

These categories are excluded from training because they are genuinely ambiguous.
Better to leave them out than to introduce label noise.

| Category | Reason |
|----------|--------|
| Painter tape | Adhesive tape roll. Cylindrical and adhesive. Does not visually resemble paper sheets. Too ambiguous to train reliably. |
| Pen | Writing instrument. Small cylindrical plastic object. Too visually distinct from all other classes (not a container, bag, wrapper, or bottle). Forcing into an unrelated category introduces label noise. |
| Unlabeled litter | Ambiguous objects that cannot be reliably classified. Training on ambiguous labels degrades model performance. |

---

## Detection → Subclass Mapping

Which EfficientNet subclasses can appear in each YOLO detection.
Used for post-crop filtering and confidence boosting.

| YOLO Class | EfficientNet Subclasses |
|------------|----------------------|
| `battery` | Battery |
| `bottle` | Glass, Plastic |
| `can` | Metal |
| `cardboard` | Cardboard |
| `cigarette` | Miscellaneous Trash |
| `cup` | Plastic, disposable_plastic_cutlery, styrofoam_cups, styrofoam_food_containers |
| `electronic` | Air-Conditioner, Electronic Component, Electronic Device, Keyboard, Laptop, Microwave, Mobile, Mouse, PCB, Player, Printer, Refrigerator, Television, Washing Machine |
| `food_waste` | Organic |
| `glass_container` | Glass, light bulbs |
| `metal` | Metal, automobile wastes |
| `paper` | Cardboard, Paper |
| `plastic_bag` | Plastic |
| `plastic_container` | Plastic, disposable_plastic_cutlery, styrofoam_cups, styrofoam_food_containers |
| `plastic_wrapper` | Plastic |
| `textile` | Textile Trash, clothing, shoes |

