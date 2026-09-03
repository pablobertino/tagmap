"""Paso 4 de Fase 0: verificar que el adaptador lista los tags."""

from ._common import bootstrap


def main() -> None:
    _, provider = bootstrap()
    trackers = provider.list_trackers()
    if not trackers:
        print("No se encontraron dispositivos. ¿La cuenta tiene tags en Find Hub?")
        return
    w = max(len(t.name) for t in trackers)
    print(f"{'Nombre':<{w}}  provider_device_id")
    for t in trackers:
        print(f"{t.name:<{w}}  {t.provider_device_id}")


if __name__ == "__main__":
    main()
