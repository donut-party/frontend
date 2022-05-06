
# Donut Frontend

Just as an operating system provides resources like the filesystem to
address needs that are not specific to any particular application, The
Donut Frontend framework aims to address many common needs that
single page apps have in common.

The library is a layer on top of
[re-frame](https://github.com/day8/re-frame/), providing handlers,
subscriptions, components, utilities and conventions to give you a
sane, extensible starting point to deal with:

* API calls
  * performing calls
  * sharing routes between frontend and backend
  * keeping track of active calls (e.g. to show activity indicators)
  * call lifecycle
* Forms
  * storing their data in the app db
  * validation
  * the submission lifecycle
  * components that "wire things up" so you don't have to.
* URL-based navigation with HTML history
* Pagination

## API calls with sync

* helpers

Transform various calls into a dispatch on `::sync` or
`::sync-once`. Those dispatches take a `req`.

* sync signature

* sync-fx handler signature

```clojure
(defn sync-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint opts]]
  (fn [_cofx [call-opts params]]
    {:dispatch [::sync [method endpoint (build-opts opts call-opts params)]]}))
```

```clojure
(fn [_cofx [call-opts params]]
    {:dispatch [::sync [method endpoint (build-opts opts call-opts params)]]})
```

* calling directly
* building handlers
* default callbacks
* :$ctx
* subscriptions

## Forms

Donut Frontend eliminates much of the boilerplate for creating forms.

- structure
- concepts

### with-form

- the form "name"
  - used for storing form state
  - used for sync
- inputs
- fields
- submission
  - syncing
  - lifecycle / state that gets updated
  - what gets submitted - extra data

### Validation

### success

### fail

### form states

### input states

## Structuring Data

## Navigation and Routing

## Pagination

## Flows (organization)

## Dev

## Demo

## Running tests

Watch with

```
lein doo
```

Run once with

```
lein doo node test once
```
