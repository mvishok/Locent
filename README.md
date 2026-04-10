![Locent Splash](https://raw.githubusercontent.com/mvishok/locent/main/app/src/main/res/drawable/splash.gif#gh-light-mode-only)
![Locent Splash](https://raw.githubusercontent.com/mvishok/locent/main/app/src/main/res/drawable/dark_splash.gif#gh-dark-mode-only)

# Locent

Locent is a location intelligence engine that evaluates how suitable a place is based on what a person actually wants to do there.

Most tools show what exists. Locent answers whether a place is good for you.

---

## The problem

When you go to a new area, you usually don’t know:

- if food options are actually good or just visible
- whether hospitals are nearby when you need them
- how accessible transport is
- whether the area matches your lifestyle

People rely on:

- random exploration  
- reviews in languages they may not understand  
- assumptions based on incomplete information  

This leads to bad decisions, especially in unfamiliar locations.

---

## What Locent solves

Locent converts a natural language intent into a measurable score for a location.

Instead of asking:
> “What’s here?”

You can ask:
> “Is this place suitable for me?”

Example:

Input  
"I’m a student, I like food, I travel locally, and I need hospitals nearby"

Output  
- overall suitability score  
- category-wise breakdown  
- explanation of why the score is high or low  

This allows decision making based on actual data instead of guesswork.

---

## How it works

Locent combines three layers:

### 1. Real world data
Uses various APIs to fetch nearby amenities:
- food
- healthcare
- transport
- etc.

### 2. Intent understanding
Uses AI to interpret the user’s request and assign importance weights to categories.

### 3. Spatial scoring
Calculates:
- proximity (how close things are)
- density (how many exist)
- relevance (based on intent)

All of this is combined into a final score out of 100.

---

## What makes it different

- Not a map  
- Not a recommendation engine  
- Not a review platform  

Locent is a **decision support system**

It helps answer:
> “Should I choose this place?”

---

## Why this exists

Locent is built for reliability in decision making.

It removes dependency on:

* local knowledge
* language barriers
* trial-and-error exploration

Instead, it provides a factual, data-driven understanding of a place before you commit to it.

---

## Rate limiting

* Guest users (IP based): 2 requests per day
* Authenticated users: 10 requests per day

---

## Status

Active development. Core engine is functional.

Next steps include:

* comparison between locations
* better ranking of top nearby places
* Custom weights

---