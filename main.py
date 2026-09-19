"""Start the mobile API by default; the old desktop experiment is opt-in."""
import argparse


def main():
    parser = argparse.ArgumentParser(description="Neo Python service")
    parser.add_argument('--desktop', action='store_true', help='Run the legacy desktop experiment (requires separate speech models and packages)')
    args = parser.parse_args()
    if args.desktop:
        from desktop_main import main as desktop_main
        desktop_main()
        return
    import uvicorn
    from core.config import settings
    uvicorn.run('server.api:app', host=settings.api_host, port=settings.api_port, access_log=False)


if __name__ == '__main__':
    main()
