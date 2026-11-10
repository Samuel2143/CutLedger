"""Provider schema projection; canonical server validation retains every bound."""
def provider_schema(value):
    if isinstance(value, list):
        return [provider_schema(item) for item in value]
    if not isinstance(value, dict):
        return value
    # Large nested length bounds exceed the provider's constrained-decoder state
    # budget. Keep shape/types/enums; enforce lengths/numeric bounds server-side.
    omitted = {'$schema', '$id', 'title', 'description', 'minLength', 'maxLength',
               'minItems', 'maxItems', 'minimum', 'maximum'}
    result = {key: provider_schema(item) for key, item in value.items() if key not in omitted and key != 'const'}
    if 'const' in value:
        result['enum'] = [value['const']]
    return result
